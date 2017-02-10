package no.uit.metapipe.cpouta;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.StreamEndEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public final class Configuration
{

    private String osAuthName;
    private String projectName;
    private String regionName;

    private List<String> ipAdmins;
    private String ipCheck;

    private String clusterKeyFileName;
    private String bastionAvailZone;

    private String networkName;
    private String networkId;

    private String userName;

    private String bastionSecGroupName;
    private String bastionMachineName;
    private String bastionKeyName;
    private String bastionFlavor;

    private String clusterName;
    private String clusterKeyName;
    private String ambariPassword;

    private Map<String, String> master;

    private List<String> nodeGroups;
    private Map<String, String> regularHddNodes;
    private Map<String, String> ioHddSsdNodes;

    private String bastionFloatingIpId;
    private String securityGroupId;
    private String serverGroupId;

    private Map<String, String> xternFiles;

    private String imageDefault;

    private String installationPrepareScript;
    private String installationTestScript;
    private String installationLaunchScript;
    private String installationStopScript;

    private String installationPackedLocation;
    private String installationUnpackedLocation;

    private String swDiskName;
    private int swDiskSize;
    private String swDiskID;

    static String errorMessagePrefix = "ERROR detected in 'config.yml'! Check the correctness of: ";

    public static Configuration loadConfig()
    {
        try
        {
            return new Yaml().loadAs(Files.newInputStream(Paths.get("config.yml")), Configuration.class);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public String getClusterKeyFileName()
    {
        return clusterKeyFileName;
    }

    public void setClusterKeyFileName(String clusterKeyFileName)
    {
        this.clusterKeyFileName = Utils.stringValidate(clusterKeyFileName, errorMessagePrefix + "clusterKeyFileName");
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = Utils.stringValidate(userName, errorMessagePrefix + "userName");
    }

    public String getBastionAvailZone()
    {
        return bastionAvailZone;
    }

    public void setBastionAvailZone(String bastionAvailZone)
    {
        this.bastionAvailZone = Utils.stringValidate(bastionAvailZone, errorMessagePrefix + "bastionAvailZone");
    }

    public String getNetworkName()
    {
        return networkName;
    }

    public void setNetworkName(String networkName)
    {
        this.networkName = Utils.stringValidate(networkName, errorMessagePrefix + "networkName");
    }

    public String getNetworkId()
    {
        return networkId;
    }

    public void setNetworkId(String networkId)
    {
        this.networkId = Utils.stringValidate(networkId, errorMessagePrefix + "networkId");
    }

    public String getBastionSecGroupName()
    {
        return bastionSecGroupName;
    }

    public void setBastionSecGroupName(String bastionSecGroupName)
    {
        this.bastionSecGroupName = Utils.stringValidate(bastionSecGroupName, errorMessagePrefix + "bastionSecGroupName");
    }

    public String getBastionMachineName()
    {
        return bastionMachineName;
    }

    public void setBastionMachineName(String bastionMachineName)
    {
        this.bastionMachineName = Utils.stringValidate(bastionMachineName, errorMessagePrefix + "bastionMachineName");
    }

    public String getBastionKeyName()
    {
        return bastionKeyName;
    }

    public void setBastionKeyName(String bastionKeyName)
    {
        this.bastionKeyName = Utils.stringValidate(bastionKeyName, errorMessagePrefix + "bastionKeyName");
    }

    public String getBastionFlavor()
    {
        return bastionFlavor;
    }

    public void setBastionFlavor(String bastionFlavor)
    {
        this.bastionFlavor = Utils.stringValidate(bastionFlavor, errorMessagePrefix + "bastionFlavor");
    }

    public String getClusterName()
    {
        return clusterName;
    }

    public void setClusterName(String clusterName)
    {
        this.clusterName = Utils.stringValidate(clusterName, errorMessagePrefix + "clusterName");
    }

    public String getClusterKeyName()
    {
        return clusterKeyName;
    }

    public void setClusterKeyName(String clusterKeyName)
    {
        this.clusterKeyName = Utils.stringValidate(clusterKeyName, errorMessagePrefix + "clusterKeyName");
    }

    public String getAmbariPassword()
    {
        return ambariPassword;
    }

    public void setAmbariPassword(String ambariPassword)
    {
        this.ambariPassword = Utils.stringValidate(ambariPassword, errorMessagePrefix + "ambariPassword");
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
        Utils.updateFileValue("config.yml", "serverGroupId", serverGroupId);
    }

    public Map<String, String> getXternFiles()
    {
        return xternFiles;
    }

    public void setXternFiles(Map<String, String> xternFiles)
    {
        Utils.objectValidate(xternFiles, errorMessagePrefix + "xternFiles");
        this.xternFiles = new HashMap<String, String>();
        for(String k : xternFiles.keySet())
        {
            Utils.stringValidate(xternFiles.get(k), errorMessagePrefix + "xternFiles " + k);
            this.xternFiles.put(k, (new File(xternFiles.get(k))).getAbsolutePath());
        }
    }

    public String getOsAuthName()
    {
        return osAuthName;
    }

    public void setOsAuthName(String osAuthName)
    {
        this.osAuthName = Utils.stringValidate(osAuthName, errorMessagePrefix + "osAuthName");
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = Utils.stringValidate(projectName, errorMessagePrefix + "projectName");
    }

    public String getRegionName()
    {
        return regionName;
    }

    public void setRegionName(String regionName)
    {
        this.regionName = Utils.stringValidate(regionName, errorMessagePrefix + "regionName");
    }

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
        Utils.updateFileValue("config.yml", "securityGroupId", securityGroupId);
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
        Utils.updateFileValue("config.yml", "bastionFloatingIpId", bastionFloatingIpId);
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

    public void addIpAdmins(List<String> newIps)
    {
        for(String s : newIps)
        {
            if(s != null && !s.isEmpty() && !this.ipAdmins.contains(s))
            {
                this.ipAdmins.add(s);
                Utils.addNewListFileEntry("config.yml", "ipAdmins", s);
            }
        }
    }

    public void removeIpAdmins(List<String> ips)
    {
        for(String s : ips)
        {
            if(s != null && !s.isEmpty() && this.ipAdmins.contains(s))
            {
                this.ipAdmins.remove(s);
                Utils.updateFileValue("config.yml", s, null);
            }
        }
    }

    public String getIpCheck()
    {
        return ipCheck;
    }

    public void setIpCheck(String ipCheck)
    {
        this.ipCheck = Utils.stringValidate(ipCheck, errorMessagePrefix + "ipCheck");
    }

    public Map<String, String> getRegularHddNodes()
    {
        return regularHddNodes;
    }

    public void setRegularHddNodes(Map<String, String> regularHddNodes)
    {
        this.regularHddNodes = (Map<String, String>)Utils.objectValidate(regularHddNodes, errorMessagePrefix + "regularHddNodes");
        if(this.getNodeGroups().contains("regularHddNodes"))
        {
            Utils.stringValidate(this.regularHddNodes.get("numNodes"), errorMessagePrefix + "ioHddSsdNodes numNodes");
        }
        else
        {
            this.regularHddNodes.put("numNodes", "0");
        }
        Utils.stringValidate(this.regularHddNodes.get("flavor"), errorMessagePrefix + "regularHddNodes flavor");
        Utils.stringValidate(this.regularHddNodes.get("volumeSize"), errorMessagePrefix + "regularHddNodes volumeSize");
    }

    public Map<String, String> getIoHddSsdNodes()
    {
        return ioHddSsdNodes;
    }

    public void setIoHddSsdNodes(Map<String, String> ioHddSsdNodes)
    {
        this.ioHddSsdNodes = (Map<String, String>)Utils.objectValidate(ioHddSsdNodes, errorMessagePrefix + "ioHddSsdNodes");
        if(this.getNodeGroups().contains("ioHddSsdNodes"))
        {
            Utils.stringValidate(this.ioHddSsdNodes.get("numNodes"), errorMessagePrefix + "ioHddSsdNodes numNodes");
        }
        else
        {
            this.ioHddSsdNodes.put("numNodes", "0");
        }
        Utils.stringValidate(this.ioHddSsdNodes.get("flavor"), errorMessagePrefix + "ioHddSsdNodes flavor");
        Utils.stringValidate(this.ioHddSsdNodes.get("hddVolumeSize"), errorMessagePrefix + "ioHddSsdNodes hddVolumeSize");
    }

    public List<String> getNodeGroups()
    {
        return nodeGroups;
    }

    public void setNodeGroups(List<String> nodeGroups)
    {
        this.nodeGroups = (List<String>)Utils.objectValidate(nodeGroups, errorMessagePrefix + "nodeGroups");
    }

    public Map<String, String> getMaster()
    {
        return master;
    }

    public void setMaster(Map<String, String> master)
    {
        this.master = (Map<String, String>)Utils.objectValidate(master, errorMessagePrefix + "master");
        Utils.stringValidate(this.master.get("flavor"), errorMessagePrefix + "master flavor");
        Utils.stringValidate(this.master.get("metadataVolumeSize"), errorMessagePrefix + "master metadataVolumeSize");
        Utils.stringValidate(this.master.get("nfsVolumeSize"), errorMessagePrefix + "master nfsVolumeSize");
    }

    public String getImageDefault()
    {
        return imageDefault;
    }

    public void setImageDefault(String imageDefault)
    {
        this.imageDefault = Utils.stringValidate(imageDefault, errorMessagePrefix + "imageDefault");
    }

    public String getInstallationPrepareScript()
    {
        return installationPrepareScript;
    }

    public void setInstallationPrepareScript(String installationPrepareScript)
    {
        this.installationPrepareScript =
                Utils.stringValidate(installationPrepareScript, errorMessagePrefix + "installationPrepareScript");
    }

    public String getInstallationTestScript()
    {
        return installationTestScript;
    }

    public void setInstallationTestScript(String installationTestScript)
    {
        this.installationTestScript =
                Utils.stringValidate(installationTestScript, errorMessagePrefix + "installationTestScript");
    }

    public String getInstallationLaunchScript()
    {
        return installationLaunchScript;
    }

    public void setInstallationLaunchScript(String installationLaunchScript)
    {
        this.installationLaunchScript =
                Utils.stringValidate(installationLaunchScript, errorMessagePrefix + "installationLaunchScript");
    }

    public String getInstallationStopScript()
    {
        return installationStopScript;
    }

    public void setInstallationStopScript(String installationStopScript)
    {
        this.installationStopScript =
                Utils.stringValidate(installationStopScript, errorMessagePrefix + "installationStopScript");
    }

    public String getInstallationPackedLocation()
    {
        return installationPackedLocation;
    }

    public void setInstallationPackedLocation(String installationPackedLocation)
    {
        this.installationPackedLocation =
                Utils.stringValidate(installationPackedLocation, errorMessagePrefix + "installationPackedLocation");
    }

    public String getInstallationUnpackedLocation()
    {
        return installationUnpackedLocation;
    }

    public void setInstallationUnpackedLocation(String installationUnpackedLocation)
    {
        this.installationUnpackedLocation =
            Utils.stringValidate(installationUnpackedLocation, errorMessagePrefix + "installationUnpackedLocation");
    }

    public String getSwDiskName()
    {
        return swDiskName;
    }

    public void setSwDiskName(String swDiskName)
    {
        this.swDiskName = Utils.stringValidate(swDiskName, errorMessagePrefix + "swDiskName");
    }

    public int getSwDiskSize()
    {
        return swDiskSize;
    }

    public void setSwDiskSize(int swDiskSize)
    {
        this.swDiskSize = Utils.nonZeroIntValidate(swDiskSize, errorMessagePrefix + "swDiskSize");
    }

    public String getSwDiskID()
    {
        return swDiskID;
    }

    public void setSwDiskID(String swDiskID)
    {
        this.swDiskID = swDiskID;
    }

    public void setAndUpdateSwDiskID(String swDiskID)
    {
        if(swDiskID == null)
        {
            swDiskID = "";
        }
        this.swDiskID = swDiskID;
        Utils.updateFileValue("config.yml", "swDiskID", swDiskID);
    }
}
