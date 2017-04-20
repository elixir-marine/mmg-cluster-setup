package no.uit.metapipe.cpouta;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.openstack.nova.v2_0.domain.*;
import org.jclouds.openstack.nova.v2_0.domain.Volume;
import org.jclouds.openstack.nova.v2_0.domain.VolumeAttachment;
import org.jclouds.openstack.nova.v2_0.extensions.*;
//import org.jclouds.openstack.nova.v2_0.extensions.ServerGroupApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.nova.v2_0.options.CreateVolumeOptions;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;



class ClientProcedures
{

    private ClientProcedures() { }

    static void bastionSecGroupCreate(SecurityGroupApi securityGroupApi, Configuration config)
    {
        System.out.println("Start creating bastion security group.");
        FluentIterable tempList = securityGroupApi.list();
        SecurityGroup temp;
        for(int i = 0; i < tempList.size(); i++)
        {
            temp = (SecurityGroup)tempList.get(i);
            if(temp.getName().equals(config.getBastionSecGroupName()))
            {
                System.out.println(config.getBastionSecGroupName() + " security group already exists.");
                return;
            }
            else if(i == tempList.size() - 1)
            {
                System.out.println("Creating security group " + config.getBastionSecGroupName());
            }
        }
        temp = securityGroupApi.createWithDescription(config.getBastionSecGroupName(), config.getBastionSecGroupName());
        config.setAndUpdateSecurityGroupId(temp.getId());
        securityGroupApi.createRuleAllowingCidrBlock(config.getSecurityGroupId(),
                Ingress.builder().fromPort(22).toPort(22).ipProtocol(IpProtocol.TCP).build(),
                "0.0.0.0/0");
        securityGroupApi.createRuleAllowingCidrBlock(config.getSecurityGroupId(),
                Ingress.builder().fromPort(-1).toPort(-1).ipProtocol(IpProtocol.ICMP).build(),
                "0.0.0.0/0");
        System.out.println(config.getBastionSecGroupName() + " security group created, rules added. Security group list:");
        for(int i = 0; i < tempList.size(); i++)
        {
            System.out.println(tempList.get(i));
        }
    }

    static void bastionKeyPairCreate(JSch ssh, String sshFolder, String bastionKeyName, KeyPairApi keyPairApi)
    {
        System.out.println("Start creating bastion key pair.");
        FluentIterable<org.jclouds.openstack.nova.v2_0.domain.KeyPair> tempList = keyPairApi.list();
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : tempList)
        {
            if(k.toString().contains(bastionKeyName))
            {
                System.out.println("Found already existing openstack keypair.");
                return;
            }
        }
        Utils.createNewKeyPair("id_rsa", sshFolder, ssh);
        System.out.println("Adding the key pair to Openstack...");
        try
        {
            keyPairApi.createWithPublicKey(bastionKeyName,
                    new String(Files.readAllBytes(Paths.get(sshFolder + "/" + "id_rsa.pub"))));
        }
        catch (IOException e)
        {
            e.printStackTrace();
            exit(1);
        }
        System.out.println("Bastion key pair added. Key list:");
        tempList = keyPairApi.list();
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : tempList)
        {
            System.out.println(k);
        }
    }

    static Server bastionServerCreate(ServerApi serverApi, ImageApi imageApi, FlavorApi flavorApi, Configuration config, JSch ssh)
    {
        System.out.println("Start creating bastion server.");
        ImmutableList<Server> tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            if(s.getName().equals(config.getBastionMachineName()))
            {
                System.out.println("Bastion server already exists.");
                return s;
            }
        }
        ImmutableList<Image> tempImageList = imageApi.listInDetail().get(0).toList();
        Image chosenImage = null;
        ImmutableList<Flavor> tempFlavorList = flavorApi.listInDetail().get(0).toList();
        Flavor chosenFlavor = null;
        CreateServerOptions createServerOptions = new CreateServerOptions();
        System.out.println("List of available images:");
        for(Image i : tempImageList)
        {
            System.out.println(i);
            if(i.getName().equals(config.getImageDefault()))
            {
                chosenImage = i;
                break;
            }
        }
        if(chosenImage == null)
        {
            System.out.println("Required image not found. Impossible to continue.");
            exit(1);
        }
        else
        {
            System.out.println("Chosen image: " + chosenImage);
        }
        System.out.println("List of available flavors:");
        for(Flavor f : tempFlavorList)
        {
            System.out.println(f);
            if(f.getName().equals(config.getBastionFlavor()))
            {
                chosenFlavor = f;
                break;
            }
        }
        if(chosenFlavor == null)
        {
            System.out.println("Required flavor not found. Impossible to continue.");
            exit(1);
        }
        else
        {
            System.out.println("Chosen flavor: " + chosenFlavor);
        }
        System.out.println("Creating instance...");
        ServerCreated bastionCreated = serverApi.create(
                config.getBastionMachineName(),
                chosenImage.getId(),
                chosenFlavor.getId(),
                createServerOptions.availabilityZone(config.getBastionAvailZone()).
                        keyPairName(config.getBastionKeyName()).
                        securityGroupNames(config.getBastionSecGroupName(), "default").
                        networks(config.getNetworkId()));
        while(serverApi.get(bastionCreated.getId()) == null ||
                serverApi.get(bastionCreated.getId()).getStatus() != Server.Status.ACTIVE)
        {
            // wait for bastion to get created
            try
            {
                sleep(1000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        System.out.println("Bastion server created. List of all currently running servers/instances:");
        tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            System.out.println(s);
        }
        return serverApi.get(bastionCreated.getId());
    }

    static void disablePasswordAuth(JSch ssh, Configuration config, Server server)
    {
        System.out.println("Start disabling password auth on " + server.getName() + " ...");
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("disablePasswordAuth")) + " 2>&1;";
        if(server != null)
        {
            String ip = null;
            if(server.getName().equals(config.getBastionMachineName()))
            {
                ip = Utils.getServerPublicIp(server, config.getNetworkName());
            }
            else if(server.getName().contains(config.getClusterName() + "-master"))
            {
                ip = Utils.getServerPrivateIp(server, config.getNetworkName());
            }
            else
            {
                System.out.println("Unexpected error!");
                exit(-1);
            }
            Utils.sshExecutor(ssh, config.getUserName(), ip, commands);
        }
        else
        {
            System.out.println("Not possible to complete, the provided server (" + server.getName() + ") is null!");
            return;
        }
        System.out.println("Password auth on " + server.getName() + " disabled.");
    }

    static void bastionIpAllocate(Configuration config, FloatingIPApi floatingIPApi, Server bastion)
    {
        System.out.println("Start allocating floating IP for Bastion. List of all floating IP addresses:");
        if(Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            FluentIterable<FloatingIP> tempList = floatingIPApi.list();
            FloatingIP floatingIP = floatingIPApi.allocateFromPool("public");
            for(FloatingIP f : tempList)
            {
                System.out.println(f);
            }
            if(floatingIP == null)
            {
                System.out.println("Could not allocate floating IP for Bastion. Can't continue.");
                exit(1);
            }
            floatingIPApi.addToServer(floatingIP.getIp(), bastion.getId());
            config.setAndUpdateBastionFloatingIpId(floatingIP.getId());
            System.out.println("Floating IP for Bastion server is allocated. Chosen IP: " + floatingIP.getIp());
        }
        else
        {
            System.out.println("Floating IP already allocated for the existing Bastion. IP: " +
                    Utils.getServerPublicIp(bastion, config.getNetworkName()));
        }
    }

    static void bastionPackagesInstall(JSch ssh, Server bastion, Configuration config, ServerApi serverApi)
    {
        System.out.println("Start setting up Bastion.");
        String commands =
                "sudo yum install -y dstat lsof bash-completion time tmux git xauth screen nano vim bind-utils " +
                        "nmap-ncat git xauth firefox centos-release-openstack  python-novaclient python-openstackclient " +
                        "python-devel python-setuptools python-virtualenvwrapper libffi-devel openssl-devel " +
                        "java-1.8.0-openjdk;" +
                "sudo yum groupinstall -y 'Development Tools';" +
                "sudo yum update -y;" +
                "sudo reboot;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Waiting for Bastion to restart...");
        while(serverApi.get(bastion.getId()).getStatus() != Server.Status.ACTIVE)
        {
            try
            {
                sleep(1000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        System.out.println("Bastion is set up.");
    }

    static void bastionAnsibleInstall(JSch ssh, Configuration config, String bastionPublicIp)
    {
        System.out.println("Start installing Ansible on Bastion.");
        String commands = "cd ~;" +
                "export PATH=/usr/bin:$PATH;" +
                "source /usr/bin/virtualenvwrapper.sh;" +
                "mkvirtualenv --system-site-packages ansible-2.3;" +
                "pip install --upgrade pip;" +
                "pip install setuptools;" +
                "pip install --upgrade setuptools;" +
                //"pip install 'ansible<2.3';" +
                "pip install git+git://github.com/ansible/ansible.git@stable-2.3;" +
                "pip install shade dnspython funcsigs functools32;" +
                "pip install python-openstackclient;" +
                "pip install python-novaclient;";
        Utils.sshExecutor(ssh, config.getUserName(), bastionPublicIp, commands);
        System.out.println("Ansible is installed on Bastion.");
    }

    static void bastionClusterKeyCreate(JSch ssh, Configuration config, KeyPairApi keyPairApi, Server bastion, String tempFolder)
    {
        System.out.println("Start creating cluster key pair.");
        String privateKey = tempFolder + "/" + config.getClusterKeyFileName();
        String pubKey = tempFolder + "/" + config.getClusterKeyFileName() + ".pub";
        File privateKeyFile = new File(privateKey);
        File pubKeyFile = new File(pubKey);
        FluentIterable<org.jclouds.openstack.nova.v2_0.domain.KeyPair> tempList = keyPairApi.list();
        String commands;
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : tempList)
        {
            if(k.toString().contains(config.getClusterKeyName()))
            {
                System.out.println("Found already existing openstack keypair, removing.");
                keyPairApi.delete(config.getClusterKeyName());
            }
        }
        commands = "printf '.ssh folder Before rm:\n';" +
                "ls ~/.ssh/;" +
                "rm ~/.ssh/" + config.getClusterKeyFileName() + ";" +
                "rm ~/.ssh/" + config.getClusterKeyFileName() + ".pub;" +
                "printf '.ssh folder After rm:\n';" +
                "ls ~/.ssh/;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Generating new cluster keys...");
        try {
            KeyPair keyPair = KeyPair.genKeyPair(ssh, KeyPair.RSA);
            privateKeyFile.createNewFile();
            pubKeyFile.createNewFile();
            keyPair.writePrivateKey(privateKey);
            keyPair.writePublicKey(pubKey, "");
        } catch (JSchException e) {
            e.printStackTrace();
            exit(1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }
        System.out.println("New cluster keys generated, transfering them to Bastion...");
        Utils.sshCopier(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), new String[]{privateKey, pubKey}, "/.ssh");
        commands = "chmod 700 ~/.ssh" +
                "chmod 644 ~/.ssh/known_hosts;" +
                "chmod 600 ~/.ssh/" + config.getClusterKeyFileName() + ";" +
                "chmod 644 ~/.ssh/" + config.getClusterKeyFileName() + ".pub;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Adding the key pair to Openstack...");
        try {
            keyPairApi.createWithPublicKey(config.getClusterKeyName(), new String(Files.readAllBytes(Paths.get(pubKey))));
        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }
        System.out.println("Cluster key pair added. Key list:");
        tempList = keyPairApi.list();
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : tempList)
        {
            System.out.println(k);
        }
    }

    static void bastionSshConfigCreate(JSch ssh, Server bastion, Configuration config, String tempFolder)
    {
        System.out.println("Start modifying Bastion SSH config file.");
        String ip2register = Utils.getServerPrivateIp(bastion, config.getNetworkName());
        ip2register = ip2register.substring(0, ip2register.lastIndexOf(".")) + ".*";
        String commands = "rm ~/.ssh/config;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(tempFolder + "/config", "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            exit(1);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            exit(1);
        }
        writer.println();
        writer.println("Host " + ip2register);
        writer.println();
        writer.println("    StrictHostKeyChecking no");
        writer.println("    IdentitiesOnly yes");
        writer.println("    IdentityFile ~/.ssh/" + config.getClusterKeyFileName());
        writer.println();
        writer.close();
        Utils.sshCopier(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), new String[]{tempFolder + "/config"}, "/.ssh");
        commands = "printf 'Contents of the new SSH config file:\n';" +
                "chmod 600 ~/.ssh/config;" +
                "cat ~/.ssh/config;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Bastion SSH config file is modified.");
    }

//    static void clusterServerGroupCreate(Configuration config, ServerGroupApi serverGroupApi)
//    {
//        if(config.getServerGroupId() != null)
//        {
//            System.out.println("Cluster server group already registered, no need to create a new one.");
//            return;
//        }
//        System.out.println("Start creating cluster server group.");
//        FluentIterable<ServerGroup> tempList;
//        String groupName = config.getClusterName() + "-common";
//        ServerGroup temp = serverGroupApi.create(groupName, Arrays.asList("anti-affinity"));
//        int i = 0;
//        config.setAndUpdateServerGroupId(temp.getId());
//        tempList = serverGroupApi.list();
//        while(!((ServerGroup)tempList.get(i)).getId().equals(config.getServerGroupId()))
//        {
//            if(i == tempList.size() - 1)
//            {
//                // wait for the group to appear in the list
//                try
//                {
//                    sleep(1000);
//                    tempList = serverGroupApi.list();
//                }
//                catch (InterruptedException e)
//                {
//                    e.printStackTrace();
//                }
//            }
//            i = (i + 1) % tempList.size();
//        }
//        System.out.println("'" + groupName + "' server group created. Server group list:");
//        for(ServerGroup s : tempList)
//        {
//            System.out.println(s);
//        }
//    }

    static void prepareToolComponents(Configuration config, String tempFolder, FlavorApi flavorApi, boolean all)
    {
        prepareToolComponents(config, tempFolder, flavorApi, all, false);
    }

    static void prepareToolComponents(Configuration config, String tempFolder, FlavorApi flavorApi, boolean all, boolean isSilent)
    {
        if(!isSilent)
        {
            System.out.println();
        }
        System.out.print("Updating tool components according to the config ...");
        if(!isSilent)
        {
            System.out.println();
        }
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "SPARK_JOB_TAG",
                "\"" + config.getSwJobTag() + "\"",
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "SW_EXECUTABLE",
                "\"" + config.getSwExecutable() + "\"",
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "SW_MAIN_DIR",
                Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()),
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "SW_TMP_DIR",
                Utils.getFileNameFromPath(config.getNfsSwTmpVolumeMount()),
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "SW_FILES_DIR_NAME",
                "\"" + config.getSwFilesDirName() + "\"",
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "ARTIFACTS_FILES_EXEC",
                "\"" + config.getSwArtifactsFilesExec() + "\"",
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + "/" + config.getSwInitScript(),
                "ARTIFACTS_FILES_DEPS_ARC",
                "\"" + config.getSwArtifactsFileDepsArc() + "\"",
                "=", true, isSilent);
        Utils.updateFileValue(
                Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                "SPARK_FILES_DIR",
                config.getSparkFilesDir(),
                "=", true, isSilent);
        if(all)
        {
            int newVal;
            Flavor tempFlavor;

            Utils.updateFileValue(
                    Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                    "CLUSTER_NAME",
                    "\"" + config.getClusterName() + "\"",
                    "=", true, isSilent);
            newVal = (config.getSparkMasterVmCores() == 0) ?
                    Utils.getFlavorByName(flavorApi, config.getMaster().get("flavor")).getVcpus() :
                    config.getSparkMasterVmCores();
            Utils.updateFileValue(
                    Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                    "CORES_MASTER",
                    Integer.toString(newVal),
                    "=", true, isSilent);
            newVal = (config.getSparkMasterVmRam() == 0) ?
                    Utils.getFlavorByName(flavorApi, config.getMaster().get("flavor")).getRam() :
                    config.getSparkMasterVmRam();
            Utils.updateFileValue(
                    Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                    "RAM_MASTER",
                    Integer.toString(newVal),
                    "=", true, isSilent);
            if(config.getIoHddSsdNodes().get("numNodes").equals("0"))
            {
                tempFlavor = Utils.getFlavorByName(flavorApi, config.getRegularHddNodes().get("flavor"));
            }
            else
            {
                tempFlavor = Utils.getFlavorByName(flavorApi, config.getIoHddSsdNodes().get("flavor"));
            }
            newVal = (config.getSparkWorkerVmCores() == 0) ? tempFlavor.getVcpus() : config.getSparkWorkerVmCores();
            Utils.updateFileValue(
                    Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                    "CORES_PER_SLAVE",
                    Integer.toString(newVal),
                    "=", true, isSilent);
            newVal = (config.getSparkWorkerVmRam() == 0) ? tempFlavor.getRam() : config.getSparkWorkerVmRam();
            Utils.updateFileValue(
                    Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                    "RAM_PER_SLAVE",
                    Integer.toString(newVal),
                    "=", true, isSilent);
            Utils.updateFileValue(
                    Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")),
                    "CORES_PER_EXECUTOR",
                    Integer.toString(config.getSparkExecutorCores()),
                    "=", true, isSilent);
            createClusterVarsFile(config, tempFolder);
        }
        System.out.println("... Done.\n");
    }

    static void createClusterVarsFile(Configuration config, String tempFolder)
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
                    newLines.add(Utils.updateYamlFileLine(lines.get(i + 1), "- directory", config.getNfsSwMainVolumeMount()));
                    newLines.add(lines.get(i + 2));
                    newLines.add(lines.get(i + 3));
                    newLines.add(Utils.updateYamlFileLine(lines.get(i + 4), "- directory", config.getNfsSwTmpVolumeMount()));
                    i += 5;
                }
                else if(line.contains("master:"))
                {
                    newLines.add(line);
                    for(j = i+1; !line.contains("node_groups:"); )
                    {
                        line = lines.get(j);
                        if(line.contains("flavor:"))
                        {
                            newLines.add(Utils.updateYamlFileLine(line, "flavor", config.getMaster().get("flavor")));
                            j++;
                        }
                        else if(line.contains("image:"))
                        {
                            newLines.add(Utils.updateYamlFileLine(line, "image", config.getImageDefault()));
                            j++;
                        }
                        else if(line.contains("name: metadata"))
                        {
                            newLines.add(line);
                            line = lines.get(j+1);
                            newLines.add(Utils.updateYamlFileLine(line, "size", config.getMaster().get("metadataVolumeSize")));
                            j = lines.indexOf(line) + 1;
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
                                newLines.add(Utils.updateYamlFileLine(lines.get(j+1), "size", config.getMaster().get("nfsSwMainVolumeSize")));
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
                        for(j = i+1; !line.contains("ssd:"); j++)
                        {
                            line = lines.get(j);
                            if(line.contains("flavor:"))
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
                            else if(line.contains("name: datavol"))
                            {
                                newLines.add(line);
                                line = lines.get(++j);
                                newLines.add(Utils.updateYamlFileLine(line, "size", config.getRegularHddNodes().get("volumeSize")));
                                break;
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
                            if(line.contains("flavor:"))
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
                            else if(line.contains("name: datavol"))
                            {
                                newLines.add(line);
                                line = lines.get(++j);
                                newLines.add(Utils.updateYamlFileLine(line, "size", config.getIoHddSsdNodes().get("hddVolumeSize")));
                                break;
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

    static void updateToolComponentsOnBastion(JSch ssh, Configuration config, Server bastion, File tempFolder, File logsFolder)
    {
        System.out.println("Start updating tool components on Bastion...");
        TarArchiver aTar = new TarArchiver();
        String commands;
        commands =
            "rm arc.tar;" +
                "rm -r " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) + ";" +
                "rm -r temp;" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterTestScript")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("thisJar")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("config")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVarsTemplate")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScript")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScriptInit")) + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        File arc = new File(tempFolder + "/arc.tar");
        DefaultFileSet fs = new DefaultFileSet();
        if(arc.exists())
        {
            arc.delete();
        }
        fs.setDirectory(new File("."));
        fs.setExcludes(new String[]{
                tempFolder.getName() + "/*", tempFolder.getName(),
                logsFolder.getName() + "/*", logsFolder.getName(),
                "trash/*", "trash"
        });
        fs.setIncludingEmptyDirectories(true);
        aTar.addFileSet(fs);
        try
        {
            aTar.addFile(new File(tempFolder + "/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars"))),
                    Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")));
        }
        catch (ArchiverException e) { } ;
        aTar.setDestFile(arc);
        try
        {
            aTar.createArchive();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return;
        }
        Utils.sshCopier(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()),
                new String[]{arc.getAbsolutePath()}, "");
        commands = "tar -xf arc.tar --overwrite;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Tool components are updated on Bastion.");
    }

    static void bastionClusterProvisionExecute(JSch ssh, Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Start executing cluster provision routine on Bastion...");
        String commands =
                "source ~/.virtualenvs/ansible-2.3/bin/activate;" +
                "export ANSIBLE_HOST_KEY_CHECKING=False;" +
                "ansible-playbook -v -e @" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                    " ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/hortonworks/provision.yml 2>&1;" +
                "ansible-playbook -v -i " + config.getClusterName() + "/hortonworks-inventory -e @" +
                Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                " ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/common/wait_for_ssh.yml;" +
                "deactivate;" +
                "echo 'Cluster provision finished.';" +
                "sleep 1;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), authCommands + commands);
        System.out.println("Cluster provision routine on Bastion is finished.");
    }

    static void bastionClusterConfigurationExecute(JSch ssh, Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Start executing cluster configuration routine on Bastion...");
        String commands =
                "source ~/.virtualenvs/ansible-2.3/bin/activate;" +
                "export ANSIBLE_HOST_KEY_CHECKING=False;" +
                "ansible-playbook -v -i " + config.getClusterName() + "/hortonworks-inventory -e @" +
                Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                " ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/hortonworks/configure.yml 2>&1;" +
                "echo 'Provisioned cluster configuration finished.';" +
                "sleep 1;" +
                "deactivate;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), authCommands + commands);
        System.out.println("Cluster configuration routine on Bastion is finished.");
    }

    static boolean swDiskExists(Configuration config, VolumeApi volumeApi)
    {
        for(Volume v : volumeApi.listInDetail())
        {
            if(v.getName().equals(Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount())))
            {
                System.out.println("swDiskExists: true.");
                return true;
            }
        }
        System.out.println("swDiskExists: false.");
        return false;
    }

    static void createSwDisk(JSch ssh, Configuration config, VolumeApi volumeApi, Server bastion,
                             VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Started creating SW storage...");
        Volume vol;
        String commands;
        if(swDiskExists(config, volumeApi))
        {
            System.out.println("Volume with the name " + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + " already exists. " +
                    "If it is a mistake, check the name in config.yml and rerun the tool.");
            return;
        }
        vol = volumeApi.create(Integer.parseInt(config.getMaster().get("nfsSwMainVolumeSize")),
                CreateVolumeOptions.Builder.name(Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount())).availabilityZone(config.getBastionAvailZone()));
        config.setAndUpdateSwDiskID(vol.getId());
        while(volumeApi.get(vol.getId()) == null)
        {
            try
            {
                sleep(1000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                exit(0);
            }
        }
        attachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("diskHelper")) +
                " " + config.getSwDiskID() + " create " + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println("Finished creating SW storage with the name '" + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + "'.");
    }

    static boolean isAttachedSwDisk(Configuration config, Server server, VolumeAttachmentApi volumeAttachmentApi)
    {
        if(volumeAttachmentApi.getAttachmentForVolumeOnServer(config.getSwDiskID(), server.getId()) != null)
        {
            System.out.println("\nisAttachedSwDisk: Disk is attached to " + server.getName() + "\n");
            return true;
        }
        System.out.println("\nisAttachedSwDisk: Disk is not attached to " + server.getName() + "\n");
        return false;
    }

    static VolumeAttachment attachSwDisk(Configuration config, Server server, VolumeApi volumeApi,
                                                 VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Attaching swDisk to " + server.getName() + "...");
        VolumeAttachment va;
        if(isAttachedSwDisk(config, server, volumeAttachmentApi))
        {
            return null;
        }
        va = volumeAttachmentApi.attachVolumeToServerAsDevice(config.getSwDiskID(), server.getId(), "/dev/vdx");
        while(!volumeApi.get(config.getSwDiskID()).getStatus().equals(Volume.Status.IN_USE))
        {
            try
            {
                sleep(2000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                exit(0);
            }
        }
        System.out.println("Disk is attached. Device: " + va.getDevice());
        return va;
    }

    static void detachSwDisk(Configuration config, Server server, VolumeApi volumeApi,
                                        VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Detaching swDisk from " + server.getName() + "...");
        if(!isAttachedSwDisk(config, server, volumeAttachmentApi))
        {
            System.out.println("Disk is already detached from " + server.getName());
            return;
        }
        volumeAttachmentApi.detachVolumeFromServer(config.getSwDiskID(), server.getId());
        while(volumeApi.get(config.getSwDiskID()).getStatus().equals(Volume.Status.IN_USE) ||
                volumeApi.get(config.getSwDiskID()).getStatus().equals(Volume.Status.DELETING) ||
                volumeApi.get(config.getSwDiskID()).getStatus().equals(Volume.Status.fromValue("Detaching")))
        {
            try
            {
                sleep(2000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                exit(0);
            }
        }
        System.out.println("Disk is detached.");
    }

    static void mountSwDisk(JSch ssh, Configuration config, Server server, VolumeAttachmentApi volumeAttachmentApi)
    {
        mountSwDisk(ssh, config, server, volumeAttachmentApi, false);
    }
    static void mountSwDisk(JSch ssh, Configuration config, Server server, VolumeAttachmentApi volumeAttachmentApi, boolean readOnly)
    {
        if(!isAttachedSwDisk(config, server, volumeAttachmentApi))
        {
            System.out.println("\nCan not mount, Disk must be attached to " + server.getName() + " first!\n");
            return;
        }
        System.out.println("Start mounting swDisk...");
        String mode = readOnly ? "mountRO" : "mount";
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("diskHelper")) +
                " " + config.getSwDiskID() + " " + mode + " " + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + " 2>&1;";
        if(server != null)
        {
            String ip = null;
            if(server.getName().equals(config.getBastionMachineName()))
            {
                ip = Utils.getServerPublicIp(server, config.getNetworkName());
            }
            else if(server.getName().contains(config.getClusterName() + "-master"))
            {
                ip = Utils.getServerPrivateIp(server, config.getNetworkName());
            }
            else { exit(-1); }
            Utils.sshExecutor(ssh, config.getUserName(), ip, commands);
        }
        else
        {
            Utils.localExecutor(commands, true);
        }
        if(readOnly)
        {
            System.out.println("swDisk mounted on /media/SW-disk in READ-ONLY mode.");
        }
        else
        {
            System.out.println("swDisk mounted on /media/" + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + " in READ-WRITE mode.");
        }
    }

    static void unmountSwDisk(JSch ssh, Configuration config, Server server)
    {
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("diskHelper")) +
                " " + config.getSwDiskID() + " unmount " + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + " 2>&1;";
        System.out.println("Start unmounting swDisk from /media/" + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + "/");
        if(server != null)
        {
            String ip = null;
            if(server.getName().equals(config.getBastionMachineName()))
            {
                ip = Utils.getServerPublicIp(server, config.getNetworkName());
            }
            else if(server.getName().contains(config.getClusterName() + "-master"))
            {
                ip = Utils.getServerPrivateIp(server, config.getNetworkName());
            }
            else { exit(-1); }
            Utils.sshExecutor(ssh, config.getUserName(), ip, commands);
        }
        else
        {
            Utils.localExecutor(commands, true);
        }
        System.out.println("swDisk unmounted from /media/" + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount()) + "/");
    }

    static void runSwDiskPreparation(JSch ssh, Configuration config, Server bastion, boolean isUpdate)
    {
        String commands = "";
        String swPath = "/media/" + Utils.getFileNameFromPath(config.getNfsSwMainVolumeMount());
        System.out.println("Start uploading SW files to temporary swDisk...");
        if(isUpdate)
        {
            commands += "cd ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + ";";
            commands += "source " + config.getSwPrepareScript() + " cleanup 2>&1;";
            Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
            commands = "";
        }
        commands += "cd ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("sw")) + ";";
        commands += "sudo chmod 777 -R " + swPath + ";";
        commands += "source " + config.getSwPrepareScript() + " sw-artifacts-prepare " +
                config.getSwArtifactsUsername() + " " + config.getSwArtifactsPassword() + " 2>&1;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("SW files are transferred to temporary swDisk.");
    }

    static void openAdminAccess(Configuration config, SecurityGroupApi securityGroupApi)
    {
        String myIP = "";
        BufferedReader in;

        MainClass.addIpMasterAccessOnOS(config.getIpAdmins(), securityGroupApi, false);
        try
        {
            in = new BufferedReader(new InputStreamReader(new URL(config.getIpCheck()).openStream()));
            myIP = in.readLine();
        }
        catch (IOException e)
        {
            System.out.println(config.getIpCheck() + " doesn't seem to work correctly!");
            e.printStackTrace();
            return;
        }
        System.out.println("Your public IP address: " + myIP);
        MainClass.addIpMasterAccess(myIP, securityGroupApi, false);
    }

    static void generateWebGuiLink(String clusterName, String masterPublicIp)
    {
        File f = new File("./" + clusterName + "_firefox.sh");
        String filePath = f.getAbsolutePath();
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(filePath, "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            exit(1);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            exit(1);
        }
        writer.println("#!/bin/bash");
        writer.println("url1=\"" + masterPublicIp + ":4040\"");
        writer.println("url2=\"" + masterPublicIp + ":8080\"");
        writer.println("firefox $url2");
        writer.println("firefox $url1");
        writer.close();
        f.setExecutable(true);
        System.out.println("URL-link to Spark GUI generated: " + f.getName());
    }

}
