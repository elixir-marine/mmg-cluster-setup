package no.uit.metapipe.cpouta;

import org.yaml.snakeyaml.Yaml;

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

    private Map<String, String> master;

    private List<String> nodeGroups;
    private Map<String, String> regularHddNodes;
    private Map<String, String> ioHddSsdNodes;

    private String bastionFloatingIpId;
    private String securityGroupId;
    private String serverGroupId;

    private Map<String, String> xternFiles;

    private String imageDefault;

    private String swInitScript;
    private String swPrepareScript;
    private String swTestScript;
    private String swLaunchScript;
    private String swStopScript;
    private String swExecutable;

    private String swOnDiskFolderName;
    private String swClusterLocation;

    private String swDiskName;
    private int swDiskSize;
    private String swDiskID;

    private String swJobTag;
    private boolean swArtifactsOnline;
    private String swArtifactsUsername;
    private String swArtifactsPassword;
    private List<String> swArtifactsLinks;

    private int sparkMasterVmCores;
    private int sparkMasterVmRam;
    private int sparkWorkerVmCores;
    private int sparkWorkerVmRam;
    private int sparkExecutorCores;

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
        Utils.updateYamlFileValue("config.yml", "serverGroupId", serverGroupId);
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
        Utils.updateYamlFileValue("config.yml", "securityGroupId", securityGroupId);
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
        Utils.updateYamlFileValue("config.yml", "bastionFloatingIpId", bastionFloatingIpId);
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
                Utils.updateYamlFileValue("config.yml", s, null);
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
            Utils.stringValidate(this.regularHddNodes.get("numNodes"), errorMessagePrefix + "regularHddNodes numNodes");
            Utils.stringValidate(this.regularHddNodes.get("volumeSize"), errorMessagePrefix + "regularHddNodes volumeSize");
        }
        else
        {
            this.regularHddNodes.put("numNodes", "0");
            this.regularHddNodes.put("volumeSize", "0");
        }
        Utils.stringValidate(this.regularHddNodes.get("flavor"), errorMessagePrefix + "regularHddNodes flavor");
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
            Utils.stringValidate(this.ioHddSsdNodes.get("hddVolumeSize"), errorMessagePrefix + "ioHddSsdNodes hddVolumeSize");
        }
        else
        {
            this.ioHddSsdNodes.put("numNodes", "0");
            this.regularHddNodes.put("hddVolumeSize", "0");
        }
        Utils.stringValidate(this.ioHddSsdNodes.get("flavor"), errorMessagePrefix + "ioHddSsdNodes flavor");
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

    public String getSwInitScript()
    {
        return swInitScript;
    }

    public void setSwInitScript(String swInitScript)
    {
        this.swInitScript =
                Utils.stringValidate(swInitScript, errorMessagePrefix + "swInitScript");
    }

    public String getSwPrepareScript()
    {
        return swPrepareScript;
    }

    public void setSwPrepareScript(String swPrepareScript)
    {
        this.swPrepareScript =
                Utils.stringValidate(swPrepareScript, errorMessagePrefix + "swPrepareScript");
    }

    public String getSwTestScript()
    {
        return swTestScript;
    }

    public void setSwTestScript(String swTestScript)
    {
        this.swTestScript =
                Utils.stringValidate(swTestScript, errorMessagePrefix + "swTestScript");
    }

    public String getSwLaunchScript()
    {
        return swLaunchScript;
    }

    public void setSwLaunchScript(String swLaunchScript)
    {
        this.swLaunchScript =
                Utils.stringValidate(swLaunchScript, errorMessagePrefix + "swLaunchScript");
    }

    public String getSwStopScript()
    {
        return swStopScript;
    }

    public void setSwStopScript(String swStopScript)
    {
        this.swStopScript =
                Utils.stringValidate(swStopScript, errorMessagePrefix + "swStopScript");
    }

    public String getSwExecutable()
    {
        return swExecutable;
    }

    public void setSwExecutable(String swExecutable)
    {
        this.swExecutable =
                Utils.stringValidate(swExecutable, errorMessagePrefix + "swExecutable");;
    }

    public String getSwOnDiskFolderName()
    {
        return swOnDiskFolderName;
    }

    public void setSwOnDiskFolderName(String swOnDiskFolderName)
    {
        this.swOnDiskFolderName =
                Utils.stringValidate(swOnDiskFolderName, errorMessagePrefix + "swOnDiskFolderName");
    }

    public String getSwClusterLocation()
    {
        return swClusterLocation;
    }

    public void setSwClusterLocation(String swClusterLocation)
    {
        this.swClusterLocation =
            Utils.stringValidate(swClusterLocation, errorMessagePrefix + "swClusterLocation");
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
        this.swDiskSize = Utils.intValidate(swDiskSize, errorMessagePrefix + "swDiskSize", false);
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
        Utils.updateYamlFileValue("config.yml", "swDiskID", swDiskID);
    }

    public boolean isSwArtifactsOnline()
    {
        return swArtifactsOnline;
    }

    public void setSwArtifactsOnline(boolean swArtifactsOnline)
    {
        this.swArtifactsOnline = swArtifactsOnline;
    }

    public String getSwArtifactsUsername()
    {
        return swArtifactsUsername;
    }

    public void setSwArtifactsUsername(String swArtifactsUsername)
    {
        if(this.isSwArtifactsOnline())
        {
            this.swArtifactsUsername = Utils.stringValidate(swArtifactsUsername, errorMessagePrefix + "swArtifactsUsername");
        }
        else
        {
            this.swArtifactsUsername = swArtifactsUsername;
        }
    }

    public String getSwArtifactsPassword()
    {
        return swArtifactsPassword;
    }

    public void setSwArtifactsPassword(String swArtifactsPassword)
    {
        if(this.isSwArtifactsOnline())
        {
            this.swArtifactsPassword = Utils.stringValidate(swArtifactsPassword, errorMessagePrefix + "swArtifactsPassword");
        }
        else
        {
            this.swArtifactsPassword = swArtifactsPassword;
        }
    }

    public List<String> getSwArtifactsLinks()
    {
        return swArtifactsLinks;
    }

    public void setSwArtifactsLinks(List<String> swArtifactsLinks)
    {
        this.swArtifactsLinks = swArtifactsLinks;

        if(this.isSwArtifactsOnline())
        {
            this.swArtifactsLinks = (List<String>)Utils.objectValidate(swArtifactsLinks, errorMessagePrefix + "swArtifactsLinks");
            for(String s : this.swArtifactsLinks)
            {
                Utils.stringValidate(s, errorMessagePrefix + "swArtifactsLinks, string " + swArtifactsLinks.indexOf(s));
            }
        }
        else
        {
            this.swArtifactsLinks = swArtifactsLinks;
        }


    }

    public String getSwJobTag()
    {
        return swJobTag;
    }

    public void setSwJobTag(String swJobTag)
    {
        this.swJobTag = Utils.stringValidate(swJobTag, errorMessagePrefix + "swJobTag");;
    }

    public int getSparkMasterVmCores()
    {
        return sparkMasterVmCores;
    }

    public void setSparkMasterVmCores(int sparkMasterVmCores)
    {
        this.sparkMasterVmCores = Utils.intValidate(sparkMasterVmCores, errorMessagePrefix + "sparkMasterVmCores", true);
    }

    public int getSparkMasterVmRam()
    {
        return sparkMasterVmRam;
    }

    public void setSparkMasterVmRam(int sparkMasterVmRam)
    {
        this.sparkMasterVmRam = Utils.intValidate(sparkMasterVmRam, errorMessagePrefix + "sparkMasterVmRam", true);
    }

    public int getSparkWorkerVmCores()
    {
        return sparkWorkerVmCores;
    }

    public void setSparkWorkerVmCores(int sparkWorkerVmCores)
    {
        this.sparkWorkerVmCores = Utils.intValidate(sparkWorkerVmCores, errorMessagePrefix + "sparkWorkerVmCores", true);
    }

    public int getSparkWorkerVmRam()
    {
        return sparkWorkerVmRam;
    }

    public void setSparkWorkerVmRam(int sparkWorkerVmRam)
    {
        this.sparkWorkerVmRam = Utils.intValidate(sparkWorkerVmRam, errorMessagePrefix + "sparkWorkerVmRam", true);
    }

    public int getSparkExecutorCores()
    {
        return sparkExecutorCores;
    }

    public void setSparkExecutorCores(int sparkExecutorCores)
    {
        this.sparkExecutorCores = Utils.intValidate(sparkExecutorCores, errorMessagePrefix + "sparkExecutorCores", false);
    }
}
