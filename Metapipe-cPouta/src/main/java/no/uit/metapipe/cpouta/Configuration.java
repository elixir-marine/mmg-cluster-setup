package no.uit.metapipe.cpouta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by alexander on 8/18/16.
 */
public final class Configuration
{

    private String osAuthName;
    private String projectName;
    private String regionName;

    private List<String> ipAdmins;
    private String ipCheck;

    private String clusterKeyFileName;
    private String bastionAvailZone;
    private String bastionImage;

    private String networkName;
    private String networkId;

    private String userName;

    private String bastionSecGroupName;
    private String bastionMachineName;
    private String bastionKeyName;
    private String bastionFlavor;
    private String clusterName;
    private String clusterKeyName;
    private String masterFlavor;
    private String masterVolumeSize;
    private String numNodes;
    private String nodeFlavor;
    private String nodeVolumeSize;
    private String volumeType;
    private String ambariPassword;

    private String bastionFloatingIpId;
    private String securityGroupId;
    private String serverGroupId;

    private Map<String, String> xternFiles;

    public String getClusterKeyFileName()
    {
        return clusterKeyFileName;
    }

    public void setClusterKeyFileName(String clusterKeyFileName)
    {
        this.clusterKeyFileName = clusterKeyFileName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getBastionAvailZone()
    {
        return bastionAvailZone;
    }

    public void setBastionAvailZone(String bastionAvailZone)
    {
        this.bastionAvailZone = bastionAvailZone;
    }

    public String getBastionImage()
    {
        return bastionImage;
    }

    public void setBastionImage(String bastionImage)
    {
        this.bastionImage = bastionImage;
    }

    public String getNetworkName()
    {
        return networkName;
    }

    public void setNetworkName(String networkName)
    {
        this.networkName = networkName;
    }

    public String getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(String networkId)
    {
        this.networkId = networkId;
    }

    public String getBastionSecGroupName()
    {
        return bastionSecGroupName;
    }

    public void setBastionSecGroupName(String bastionSecGroupName)
    {
        this.bastionSecGroupName = bastionSecGroupName;
    }

    public String getBastionMachineName()
    {
        return bastionMachineName;
    }

    public void setBastionMachineName(String bastionMachineName)
    {
        this.bastionMachineName = bastionMachineName;
    }

    public String getBastionKeyName()
    {
        return bastionKeyName;
    }

    public void setBastionKeyName(String bastionKeyName)
    {
        this.bastionKeyName = bastionKeyName;
    }

    public String getBastionFlavor()
    {
        return bastionFlavor;
    }

    public void setBastionFlavor(String bastionFlavor)
    {
        this.bastionFlavor = bastionFlavor;
    }

    public String getClusterName()
    {
        return clusterName;
    }

    public void setClusterName(String clusterName)
    {
        this.clusterName = clusterName;
    }

    public String getClusterKeyName()
    {
        return clusterKeyName;
    }

    public void setClusterKeyName(String clusterKeyName)
    {
        this.clusterKeyName = clusterKeyName;
    }

    public String getMasterFlavor()
    {
        return masterFlavor;
    }

    public void setMasterFlavor(String masterFlavor)
    {
        this.masterFlavor = masterFlavor;
    }

    public String getMasterVolumeSize()
    {
        return masterVolumeSize;
    }

    public void setMasterVolumeSize(String masterVolumeSize)
    {
        this.masterVolumeSize = masterVolumeSize;
    }

    public String getNumNodes()
    {
        return numNodes;
    }

    public void setNumNodes(String numNodes)
    {
        this.numNodes = numNodes;
    }

    public String getNodeFlavor()
    {
        return nodeFlavor;
    }

    public void setNodeFlavor(String nodeFlavor)
    {
        this.nodeFlavor = nodeFlavor;
    }

    public String getNodeVolumeSize()
    {
        return nodeVolumeSize;
    }

    public void setNodeVolumeSize(String nodeVolumeSize)
    {
        this.nodeVolumeSize = nodeVolumeSize;
    }

    public String getVolumeType()
    {
        return volumeType;
    }

    public void setVolumeType(String volumeType)
    {
        this.volumeType = volumeType;
    }

    public String getAmbariPassword()
    {
        return ambariPassword;
    }

    public void setAmbariPassword(String ambariPassword)
    {
        this.ambariPassword = ambariPassword;
    }

    public String getServerGroupId()
    {
        return serverGroupId;
    }

    public void setServerGroupId(String serverGroupId)
    {
        this.serverGroupId = serverGroupId;
    }

    public void setAndUpdateServerGroupId(String serverGroupId)
    {
        if(serverGroupId == null)
        {
            serverGroupId = "";
        }
        this.serverGroupId = serverGroupId;
        this.updateFileValue("serverGroupId", serverGroupId);
    }

    public Map<String, String> getXternFiles()
    {
        return xternFiles;
    }

    public void setXternFiles(Map<String, String> xternFiles)
    {
        this.xternFiles = new HashMap<String, String>();
        for(String k : xternFiles.keySet())
        {
            this.xternFiles.put(k, (new File(xternFiles.get(k))).getAbsolutePath());
        }
    }

    public String getOsAuthName()
    {
        return osAuthName;
    }

    public void setOsAuthName(String osAuthName)
    {
        this.osAuthName = osAuthName;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getRegionName()
    {
        return regionName;
    }

    public void setRegionName(String regionName)
    {
        this.regionName = regionName;
    }

//    public String getMasterPrivateIP()
//    {
//        return masterPrivateIP;
//    }
//
//    public void setMasterPrivateIP(String masterPrivateIP)
//    {
//        this.masterPrivateIP = masterPrivateIP;
//    }
//
//    public void setAndUpdateMasterPrivateIP(String masterPrivateIP)
//    {
//        if(masterPrivateIP == null)
//        {
//            masterPrivateIP = "";
//        }
//        this.masterPrivateIP = masterPrivateIP;
//        this.updateFileValue("masterPrivateIP", masterPrivateIP);
//    }

    public String getSecurityGroupId()
    {
        return securityGroupId;
    }

    public void setSecurityGroupId(String securityGroupId)
    {
        this.securityGroupId = securityGroupId;
    }

    public void setAndUpdateSecurityGroupId(String securityGroupId)
    {
        if(securityGroupId == null)
        {
            securityGroupId = "";
        }
        this.securityGroupId = securityGroupId;
        this.updateFileValue("securityGroupId", securityGroupId);
    }

    public String getBastionFloatingIpId()
    {
        return bastionFloatingIpId;
    }

    public void setBastionFloatingIpId(String bastionFloatingIpId)
    {
        this.bastionFloatingIpId = bastionFloatingIpId;
    }

    public void setAndUpdateBastionFloatingIpId(String bastionFloatingIpId)
    {
        if(bastionFloatingIpId == null)
        {
            bastionFloatingIpId = "";
        }
        this.bastionFloatingIpId = bastionFloatingIpId;
        this.updateFileValue("bastionFloatingIpId", bastionFloatingIpId);
    }

    public List<String> getIpAdmins()
    {
        return ipAdmins;
    }

    public void setIpAdmins(List<String> ipAdmins)
    {
        if(ipAdmins == null)
        {
            this.ipAdmins = new ArrayList<String>();
        }
        else
        {
            this.ipAdmins = ipAdmins;
        }
    }

    public void setAndUpdateIpAdmins(List<String> ipAdmins)
    {
        for(String s : ipAdmins)
        {
            if(s != null && !s.isEmpty() && !this.ipAdmins.contains(s))
            {
                this.ipAdmins.add(s);
                this.addNewListFileEntry("ipAdmins", s);
            }
        }
    }

//    public String getMasterPublicIP()
//    {
//        return masterPublicIP;
//    }
//
//    public void setMasterPublicIP(String masterPublicIP)
//    {
//        this.masterPublicIP = masterPublicIP;
//    }
//
//    public void setAndUpdateMasterPublicIP(String masterPublicIP)
//    {
//        if(masterPublicIP == null)
//        {
//            masterPublicIP = "";
//        }
//        this.masterPublicIP = masterPublicIP;
//        this.updateFileValue("masterPublicIP", masterPublicIP);
//    }

    private void updateFileValue(String valueName, String newValue)
    {
        try
        {
            List<String> lines;
            List<String> newLines = new ArrayList<String>();
            File f = new File("config.yml");
            lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
            for(String line: lines)
            {
                if(line.contains(valueName))
                {
                    newLines.add(line.substring(0, line.indexOf(valueName)) + valueName + ": " + String.valueOf(newValue));
                }
                else
                {
                    newLines.add(line);
                }
            }
            Files.write(f.toPath(), newLines, Charset.defaultCharset());
            System.out.println("Config file updated: '" + valueName + "' changed to '" + newValue + "'.");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public String getIpCheck()
    {
        return ipCheck;
    }

    public void setIpCheck(String ipCheck)
    {
        this.ipCheck = ipCheck;
    }

    private void addNewListFileEntry(String list, String newEntry)
    {
        try
        {
            List<String> lines;
            List<String> newLines = new ArrayList<String>();
            File f = new File("config.yml");
            lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
            for(String line: lines)
            {
                newLines.add(line);
                if(line.contains(list))
                {
                    newLines.add(line.substring(0, line.lastIndexOf(list)) + "  - " + newEntry);
                }
            }
            Files.write(f.toPath(), newLines, Charset.defaultCharset());
            System.out.println("Config file updated: '" + newEntry + "', new entry in '" + list + "'.");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

}
