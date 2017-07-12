package no.uit.metapipe.cpouta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

class ClusterVarsFile
{

    private ClusterVarsFile() { }

    static void create4nfsSharedSW(Configuration config, String tempFolder)
    {
        System.out.println("Generating 'cluster_vars.yaml'...");
        File templateFile = new File(config.getXternFiles().get("ansibleClusterVarsTemplate"));
        File newFile = new File(tempFolder + "/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")));
        try
        {
            Files.copy(templateFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            List<String> lines;
            List<String> newLines = new ArrayList<String>();
            String line;
            int j;
            lines = Files.readAllLines(newFile.toPath(), Charset.defaultCharset());
            loop:
            for(int i = 0; i < lines.size(); )
            {
                line = lines.get(i);
                if(line.contains("cluster_name:"))
                {
                    newLines.add(Utils.updateYamlFileLine(line, "cluster_name", config.getClusterName()));
                    i++;
                }
                else if(line.contains("network_name:"))
                {
                    newLines.add(Utils.updateYamlFileLine(line, "network_name", config.getNetworkName()));
                    i++;
                }
                else if(line.contains("ssh_key:"))
                {
                    newLines.add(Utils.updateYamlFileLine(line, "ssh_key", config.getClusterKeyName()));
                    i++;
                }
                else if(line.contains("bastion_secgroup:"))
                {
                    newLines.add(Utils.updateYamlFileLine(line, "bastion_secgroup", config.getBastionSecGroupName()));
                    i++;
                }
                else if(line.contains("nfs_shares:"))
                {
                    newLines.add(line);
                    if(config.isSwNfcShared())
                    {
                        newLines.add(Utils.updateYamlFileLine(lines.get(i + 1), "- directory", config.getNfsSwMainVolumeMount()));
                        newLines.add(lines.get(i + 2));
                        newLines.add(lines.get(i + 3));
                    }
                    newLines.add(Utils.updateYamlFileLine(lines.get(i + 4), "- directory", config.getNfsSwTmpVolumeMount()));
                    i += 5;
                }
                else if(line.contains("master:"))
                {
                    newLines.add(line);
                    for(j = i+1; !line.contains("node_groups:"); )
                    {
                        line = lines.get(j);
                        if(line.contains("server_group:"))
                        {
                            if(config.isServerGroupAntiAffinity())
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "server_group", config.getClusterName() + "-server_group"));
                            }
                            else
                            {
                                //newLines.add(Utils.updateYamlFileLine(line, "server_group", "none"));
                            }
                            j++;
                        }
                        else if(line.contains("flavor:"))
                        {
                            newLines.add(Utils.updateYamlFileLine(line, "flavor", config.getMaster().get("flavor")));
                            j++;
                        }
                        else if(line.contains("image:"))
                        {
                            newLines.add(Utils.updateYamlFileLine(line, "image", config.getImageDefault()));
                            j++;
                        }
                        else if(line.contains("name: " + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount())))
                        {
                            if(lines.get(j+2).contains("mount_path"))
                            {
                                newLines.add(line);
                                newLines.add(lines.get(j+1));
                                newLines.add(Utils.updateYamlFileLine(lines.get(j+2), "mount_path", "\"" + config.getNfsSwMainVolumeMount()) + "\"");
                                newLines.add(lines.get(j+3));
                                newLines.add(lines.get(j+4));
                                j += 5;
                            }
                            else
                            {
                                newLines.add(line);
                                newLines.add(Utils.updateYamlFileLine(lines.get(j+1), "size", String.valueOf(config.getSwVolumeSize())));
                                newLines.add(lines.get(j+2));
                                newLines.add(Utils.updateYamlFileLine(lines.get(j+3), "volume_id", config.getSwDiskID()));
                                j += 4;
                            }
                        }
                        else if(line.contains("name: " + Utils.getFileNameFromPath(config.getNfsSwTmpVolumeMount())))
                        {
                            if(lines.get(j+2).contains("mount_path"))
                            {
                                newLines.add(line);
                                newLines.add(lines.get(j+1));
                                newLines.add(Utils.updateYamlFileLine(lines.get(j+2), "mount_path", "\"" + config.getNfsSwTmpVolumeMount()) + "\"");
                                newLines.add(lines.get(j+3));
                                newLines.add(lines.get(j+4));
                                i = j + 5;
                                break;
                            }
                            else
                            {
                                newLines.add(line);
                                newLines.add(Utils.updateYamlFileLine(lines.get(j+1), "size", config.getMaster().get("nfsSwTmpVolumeSize")));
                                newLines.add(lines.get(j+2));
                                newLines.add(lines.get(j+3));
                                j += 4;
                            }
                        }
                        else
                        {
                            newLines.add(line);
                            j++;
                        }
                    }
                }
                else if(line.contains("node_groups:"))
                {
                    newLines.add(line);
                    if(config.getNodeGroups().contains("regularHddNodes"))
                    {
                        newLines.add("  - disk");
                    }
                    if(config.getNodeGroups().contains("ioHddSsdNodes"))
                    {
                        newLines.add("  - ssd");
                    }
                    i = i + 3;
                }
                else if(line.contains("disk:"))
                {
                    if(config.getNodeGroups().contains("regularHddNodes"))
                    {
                        newLines.add(line);
                        for(j = i+1; !lines.get(j).contains("ssd:"); j++)
                        {
                            line = lines.get(j);
                            if(line.contains("server_group:"))
                            {
                                if(config.isServerGroupAntiAffinity())
                                {
                                    newLines.add(Utils.updateYamlFileLine(line, "server_group", config.getClusterName() + "-server_group"));
                                }
                                else
                                {
                                    //newLines.add(Utils.updateYamlFileLine(line, "server_group", "none"));
                                }
                            }
                            else if(line.contains("flavor:"))
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "flavor", config.getRegularHddNodes().get("flavor")));
                            }
                            else if(line.contains("image:"))
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "image", config.getImageDefault()));
                            }
                            else if(line.contains("num_vms:"))
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "num_vms", config.getRegularHddNodes().get("numNodes")));
                            }
                            else if(line.contains("volumes:"))
                            {
                                if(!config.isSwNfcShared())
                                {
                                    newLines.add(line);
                                    newLines.add(lines.get(j+1));
                                    newLines.add(Utils.updateYamlFileLine(lines.get(j+2), "size", String.valueOf(config.getSwVolumeSize())));
                                    newLines.add(lines.get(j+3));
                                    newLines.add(Utils.updateYamlFileLine(lines.get(j+4), "volume_id", config.getSwDiskID()));
                                    newLines.add(lines.get(j+5));
                                    newLines.add(lines.get(j+6));
                                    newLines.add(lines.get(j+7));
                                    newLines.add(Utils.updateYamlFileLine(lines.get(j+8), "mount_path", "\"" + config.getNfsSwMainVolumeMount()) + "\"");
                                    newLines.add(lines.get(j+9));
                                    newLines.add(lines.get(j+10));
                                    //j += 11;
                                    break loop;
                                }
                                else
                                {
                                    newLines.add("");
                                    j += 11;
                                    //while(!lines.get(j).contains("ssd:")) { j++; }
                                    break;
                                }
                            }
                            else
                            {
                                newLines.add(line);
                            }
                        }
                        i = ++j;
                    }
                    else
                    {
                        while(i < lines.size() && !lines.get(i).contains("ssd:")) { i++; }
                    }
                }
                else if(line.contains("ssd:"))
                {
                    if(config.getNodeGroups().contains("ioHddSsdNodes"))
                    {
                        newLines.add(line);
                        for(j = i+1; j < lines.size(); j++)
                        {
                            line = lines.get(j);
                            if(line.contains("server_group:"))
                            {
                                if(config.isServerGroupAntiAffinity())
                                {
                                    newLines.add(Utils.updateYamlFileLine(line, "server_group", config.getClusterName() + "-server_group"));
                                }
                                else
                                {
                                    //newLines.add(Utils.updateYamlFileLine(line, "server_group", "none"));
                                }
                            }
                            else if(line.contains("flavor:"))
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "flavor", config.getIoHddSsdNodes().get("flavor")));
                            }
                            else if(line.contains("image:"))
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "image", config.getImageDefault()));
                            }
                            else if(line.contains("num_vms:"))
                            {
                                newLines.add(Utils.updateYamlFileLine(line, "num_vms", config.getIoHddSsdNodes().get("numNodes")));
                            }
                            else if(line.contains("name: " + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount())))
                            {
                                if(lines.get(j+2).contains("mount_path"))
                                {
                                    if(!config.isSwNfcShared())
                                    {
                                        newLines.add(line);
                                        newLines.add(lines.get(j+1));
                                        newLines.add(Utils.updateYamlFileLine(lines.get(j+2), "mount_path", "\"" + config.getNfsSwMainVolumeMount()) + "\"");
                                        newLines.add(lines.get(j+3));
                                        newLines.add(lines.get(j+4));
                                    }
                                    j += 4;
                                }
                                else
                                {
                                    if(!config.isSwNfcShared())
                                    {
                                        newLines.add(line);
                                        newLines.add(Utils.updateYamlFileLine(lines.get(j+1), "size", String.valueOf(config.getSwVolumeSize())));
                                        newLines.add(lines.get(j+2));
                                        newLines.add(Utils.updateYamlFileLine(lines.get(j+3), "volume_id", config.getSwDiskID()));
                                    }
                                    j += 3;
                                }
                            }
                            else
                            {
                                newLines.add(line);
                            }
                        }
                    }
                    else
                    {
                        break loop;
                    }
                    i = ++j;
                }
                else
                {
                    newLines.add(line);
                    i++;
                }
            }
            Files.write(newFile.toPath(), newLines, Charset.defaultCharset());
            System.out.println("'cluster_vars.yaml' generated:");
            BufferedReader br = new BufferedReader(new FileReader(newFile));
            while ((line = br.readLine()) != null)
            {
                System.out.println(line);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    
}
