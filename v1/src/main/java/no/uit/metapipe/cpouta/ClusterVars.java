package no.uit.metapipe.cpouta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by alexander on 8/18/16.
 */
public final class ClusterVars
{

    private String cluster_name;
    private String ssh_key;
    private String bastion_secgroup;

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
    private String ambariPassword;

    private List<String> nodeGroups;
    private Map<String, String> regularHddNodes;
    private Map<String, String> ioHddSsdNodes;

    private String bastionFloatingIpId;
    private String securityGroupId;
    private String serverGroupId;

}
