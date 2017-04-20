package no.uit.metapipe.cpouta;

import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.domain.Snapshot;
import org.jclouds.openstack.cinder.v1.domain.VolumeQuota;
import org.jclouds.openstack.keystone.v2_0.domain.Tenant;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class HardwareStats
{

    public int coresMax;
    public int coresUsed;
    public int cores4env;
    public int cores4newCluster;

    public int ramMax;
    public int ramUsed;
    public int ram4env;
    public int ram4newCluster;

    public int instancesMax;
    public int instancesUsed;
    public int instances4env;
    public int instances4newCluster;

    public int volumesMax;
    public int volumesUsed;
    public int volumes4env;
    public int volumes4newCluster;

    public int volumeStorageMax;
    public int volumeStorageUsed;
    public int volumeStorage4env;
    public int volumeStorage4newCluster;

    public int volumeSnapshotsMax;
    public int volumeSnapshotsUsed;
    public int volumeSnapshots4env;
    public int volumeSnapshots4newCluster;

    public int volumeSnapStorageMax;
    public int volumeSnapStorageUsed;
    public int volumeSnapStorage4env;
    public int volumeSnapStorage4newCluster;

    public int floatingIpMax;
    public int floatingIpUsed;
    public int floatingIp4env;
    public int floatingIp4newCluster;

    public int securityGroupsMax;
    public int securityGroupsUsed;
    public int securityGroups4env;
    public int securityGroups4newCluster;

    public int securityGroupRulesMax;
    public int securityGroupRulesUsed;
    public int securityGroupRules4env;
    public int securityGroupRules4newCluster;

    public int keyPairsMax;
    public int keyPairsUsed;
    public int keyPairs4env;
    public int keyPairs4newCluster;

    // These stats are to be implemented when server groups are implemented in jClouds
    public int serverGroupsMax;
    public int serverGroupsUsed;
    public int serverGroups4env;
    public int serverGroups4newCluster;

    HardwareStats() { }

    HardwareStats loadHardwareStats(Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
        loadHardwareStatsStatic(this, config, novaApi, tenant, cinderApi, neutronApi);
        return this;
    }

    public static HardwareStats loadHardwareStatsStatic(Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
        HardwareStats hardwareStats = new HardwareStats();
        return loadHardwareStatsStatic(hardwareStats, config, novaApi, tenant, cinderApi, neutronApi);
    }

    public static HardwareStats loadHardwareStatsStatic(HardwareStats hardwareStats, Configuration config,
                                                        NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
        Quota quota = novaApi.getQuotaApi(config.getRegionName()).get().getByTenant(tenant.getId());
        VolumeQuota volumeQuota = cinderApi.getQuotaApi(config.getRegionName()).getByTenant(tenant.getId()); // for some reason CinderApi doesn't work

        hardwareStats.coresMax = quota.getCores();
        hardwareStats.ramMax = quota.getRam();
        hardwareStats.instancesMax = quota.getInstances();
//        hardwareStats.volumesMax = quota.getVolumes();
//        hardwareStats.volumeStorageMax = quota.getGigabytes();
//        hardwareStats.volumeSnapshotsMax = hardwareStats.volumesMax;
        hardwareStats.volumesMax = volumeQuota.getVolumes();                          // for some reason CinderApi doesn't work
        hardwareStats.volumeStorageMax = volumeQuota.getGigabytes();                  // for some reason CinderApi doesn't work
        hardwareStats.volumeSnapshotsMax = volumeQuota.getSnapshots();                // for some reason CinderApi doesn't work
        hardwareStats.volumeSnapStorageMax = hardwareStats.volumeStorageMax;
        hardwareStats.floatingIpMax = quota.getFloatingIps();
        hardwareStats.securityGroupsMax = quota.getSecurityGroups();
        hardwareStats.securityGroupRulesMax = quota.getSecurityGroupRules();
        hardwareStats.keyPairsMax = quota.getKeyPairs();
        hardwareStats.serverGroupsMax = 0;

        hardwareStats.cores4env = Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getBastionFlavor()).getVcpus();
        hardwareStats.ram4env = Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getBastionFlavor()).getRam();
        hardwareStats.instances4env = 1;
        hardwareStats.volumes4env = 1;
        hardwareStats.volumeStorage4env = Integer.parseInt(config.getMaster().get("nfsSwMainVolumeSize"));
        hardwareStats.volumeSnapshots4env = 0;
        hardwareStats.volumeSnapStorage4env = 0;
        hardwareStats.floatingIp4env = 1;
        hardwareStats.securityGroups4env = 1;
        hardwareStats.securityGroupRules4env = 4;
        hardwareStats.keyPairs4env = 1;
        hardwareStats.serverGroups4env = 0;

        hardwareStats.cores4newCluster =
                Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getMaster().get("flavor")).getVcpus() +
                Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getRegularHddNodes().get("flavor")).getVcpus() *
                        Integer.parseInt(config.getRegularHddNodes().get("numNodes")) +
                Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getIoHddSsdNodes().get("flavor")).getVcpus() *
                        Integer.parseInt(config.getIoHddSsdNodes().get("numNodes"));
        hardwareStats.ram4newCluster =
                Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getMaster().get("flavor")).getRam() +
                Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getRegularHddNodes().get("flavor")).getRam() *
                        Integer.parseInt(config.getRegularHddNodes().get("numNodes")) +
                Utils.getFlavorByName(novaApi.getFlavorApi(config.getRegionName()), config.getIoHddSsdNodes().get("flavor")).getRam() *
                        Integer.parseInt(config.getIoHddSsdNodes().get("numNodes"));
        hardwareStats.instances4newCluster =
                1 /*master*/ +
                Integer.parseInt(config.getRegularHddNodes().get("numNodes")) +
                Integer.parseInt(config.getIoHddSsdNodes().get("numNodes"));
        hardwareStats.volumes4newCluster = 2 /*per-master*/ + 1 /*per-worker*/ * hardwareStats.instances4newCluster;
        hardwareStats.volumeStorage4newCluster =
                Integer.parseInt(config.getMaster().get("metadataVolumeSize")) +
                Integer.parseInt(config.getMaster().get("nfsSwMainVolumeSize")) +
                Integer.parseInt(config.getMaster().get("nfsSwTmpVolumeSize")) +
                Integer.parseInt(config.getRegularHddNodes().get("volumeSize")) * Integer.parseInt(config.getRegularHddNodes().get("numNodes")) +
                Integer.parseInt(config.getIoHddSsdNodes().get("hddVolumeSize")) * Integer.parseInt(config.getIoHddSsdNodes().get("numNodes"));
        hardwareStats.volumeSnapshots4newCluster = 0;
        hardwareStats.volumeSnapStorage4newCluster = 0;
        hardwareStats.floatingIp4newCluster = 1;
        hardwareStats.securityGroups4newCluster = 2;
        hardwareStats.securityGroupRules4newCluster = hardwareStats.securityGroups4newCluster * 4;
        hardwareStats.keyPairs4newCluster = 1;
        hardwareStats.serverGroups4newCluster = 1;

        hardwareStats.coresUsed = 0;
        hardwareStats.ramUsed = 0;
        hardwareStats.instancesUsed = 0;
        hardwareStats.volumesUsed = 0;
        hardwareStats.volumeStorageUsed = 0;
        hardwareStats.volumeSnapshotsUsed = 0;
        hardwareStats.volumeSnapStorageUsed = 0;
        hardwareStats.floatingIpUsed = 0;
        hardwareStats.securityGroupsUsed = 0;
        hardwareStats.securityGroupRulesUsed = 0;
        hardwareStats.keyPairsUsed = 0;
        hardwareStats.serverGroupsUsed = 0;
        try
        {
            if(novaApi.getSimpleTenantUsageApi(config.getRegionName()).get().get(tenant.getId()) != null)
            {
                for(SimpleServerUsage x : novaApi.getSimpleTenantUsageApi(config.getRegionName()).get().get(tenant.getId()).getServerUsages())
                {
                    hardwareStats.coresUsed += x.getFlavorVcpus();
                    hardwareStats.ramUsed += x.getFlavorMemoryMb();
                    hardwareStats.instancesUsed++;
                }
            }
            hardwareStats.volumesUsed = cinderApi.getVolumeApi(config.getRegionName()).list().size();                         // for some reason CinderApi doesn't work
            for(org.jclouds.openstack.cinder.v1.domain.Volume x : cinderApi.getVolumeApi(config.getRegionName()).list())      // for some reason CinderApi doesn't work
            {
                hardwareStats.volumeStorageUsed += x.getSize();                                                               // for some reason CinderApi doesn't work
            }
            hardwareStats.volumeSnapshotsUsed = cinderApi.getSnapshotApi(config.getRegionName()).list().size();               // for some reason CinderApi doesn't work
            for(Snapshot x : cinderApi.getSnapshotApi(config.getRegionName()).list())                                         // for some reason CinderApi doesn't work
            {
                hardwareStats.volumeSnapStorageUsed += x.getSize();                                                           // for some reason CinderApi doesn't work
            }
//            hardwareStats.volumesUsed = novaApi.getVolumeApi(config.getRegionName()).get().list().size();
//            for(Volume x : novaApi.getVolumeApi(config.getRegionName()).get().list())
//            {
//                hardwareStats.volumeStorageUsed += x.getSize();
//            }
//            hardwareStats.volumeSnapshotsUsed = novaApi.getVolumeApi(config.getRegionName()).get().listSnapshots().size();
//            for(VolumeSnapshot x : novaApi.getVolumeApi(config.getRegionName()).get().listSnapshots())
//            {
//                hardwareStats.volumeSnapStorageUsed += x.getSize();
//            }
            hardwareStats.volumeSnapStorageUsed += hardwareStats.volumeStorageUsed;
            hardwareStats.floatingIpUsed = novaApi.getFloatingIPApi(config.getRegionName()).get().list().size();
            hardwareStats.securityGroupsUsed = novaApi.getSecurityGroupApi(config.getRegionName()).get().list().size();
            for(SecurityGroup x : novaApi.getSecurityGroupApi(config.getRegionName()).get().list())
            {
                hardwareStats.securityGroupRulesUsed += x.getRules().size();
            }
            hardwareStats.keyPairsUsed = novaApi.getKeyPairApi(config.getRegionName()).get().list().size();
            hardwareStats.serverGroupsUsed = -1;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        hardwareStats.ramMax = hardwareStats.ramMax / 1024;
        hardwareStats.ramUsed = hardwareStats.ramUsed / 1024;
        hardwareStats.ram4env = hardwareStats.ram4env / 1024;
        hardwareStats.ram4newCluster = hardwareStats.ram4newCluster / 1024;

        return hardwareStats;
    }

    public void printStats(PrintStream out, Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
//        String leftAlignFormat = " | %-15s | %-9d | %-8d | %-9d | %-11d | %-16d |%n";
        String leftAlignFormat = " | %-18s | %-9s | %-10s | %-11s | %-11s | %-13s | %-11s |%n";
        String header = " |                    |   Quota   |    Used    |  Available  |  Req 4 Env  | Req 4 Cluster |  Req 4 All  |%n";
        String separator = " +--------------------+-----------+------------+-------------+-------------+---------------+-------------+%n";

        this.loadHardwareStats(config, novaApi, tenant, cinderApi, neutronApi);

        out.format(separator);
        out.format(header);
        out.format(separator);
        printStatsString(out, leftAlignFormat, "Cores", this.coresMax, this.coresUsed, this.cores4env, this.cores4newCluster);
        printStatsString(out, leftAlignFormat, "RAM (GB)", this.ramMax, this.ramUsed, this.ram4env, this.ram4newCluster);
        printStatsString(out, leftAlignFormat, "Instances", this.instancesMax, this.instancesUsed, this.instances4env, this.instances4newCluster);
        printStatsString(out, leftAlignFormat, "Volumes (#)", this.volumesMax, this.volumesUsed, this.volumes4env, this.volumes4newCluster);
        printStatsString(out, leftAlignFormat, "Volumes (GB)",
                this.volumeStorageMax, this.volumeStorageUsed, this.volumeStorage4env, this.volumeStorage4newCluster);
//        printStatsString(out, leftAlignFormat, "Vol.Snapshots (#)",
//                this.volumeSnapshotsMax, this.volumeSnapshotsUsed, this.volumeSnapshots4env, this.volumeSnapshots4newCluster);
//        printStatsString(out, leftAlignFormat, "Vol.Snapshots (GB)",
//                this.volumeSnapStorageMax, this.volumeSnapStorageUsed, this.volumeSnapStorage4env, this.volumeSnapStorage4newCluster);
        printStatsString(out, leftAlignFormat, "Floating IP", this.floatingIpMax, this.floatingIpUsed, this.floatingIp4env, this.floatingIp4newCluster);
        printStatsString(out, leftAlignFormat, "Security Groups",
                this.securityGroupsMax, this.securityGroupsUsed, this.securityGroups4env, this.securityGroups4newCluster);
        printStatsString(out, leftAlignFormat, "Sec.Group Rules",
                this.securityGroupRulesMax, this.securityGroupRulesUsed, this.securityGroupRules4env, this.securityGroupRules4newCluster);
        printStatsString(out, leftAlignFormat, "Key Pairs", this.keyPairsMax, this.keyPairsUsed, this.keyPairs4env, this.keyPairs4newCluster);
        printStatsString(out, leftAlignFormat, "Server Groups", this.serverGroupsMax, this.serverGroupsUsed, this.serverGroups4env, this.serverGroups4newCluster);
        out.format(separator);
        out.println();
    }

    private void printStatsString(PrintStream out, String format, String columnName, int max, int used, int perEnv, int perCluster)
    {
        String maxS, usedS, remainsS, perEnvS, perClusterS;
        if(max == 0)
        {
            maxS = "n/a";
            usedS = Integer.toString(used);
            remainsS = "n/a";
        }
        else
        {
            maxS = Integer.toString(max);
            usedS = Integer.toString(used) + " (" + percentString(max, used) + ")";
            remainsS = Integer.toString(max - used) + " (" + percentString(max, max - used) + ")";
        }
        if(used > max)
        {
            maxS += " ERR!";
        }
        if(used == -1)
        {
            usedS = "n/a";
        }
        perEnvS = Integer.toString(perEnv);
        perClusterS = Integer.toString(perCluster);

        out.format(format, columnName, maxS, usedS, remainsS, perEnvS, perClusterS, Integer.toString(perEnv + perCluster));
    }

    private String percentString(float full, float part)
    {
        return Integer.toString((int)(part / full * 100)) + "%";
    }

    public boolean canCreateEnv(PrintStream out, Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
        out.println("\nResources validation for '" + MainClass.Commands.CREATE_ENV.getCommand().toUpperCase() + "' started...\n");
        boolean res = canCreateValidation(out, config, novaApi, tenant, cinderApi, neutronApi,
                cores4env, ram4env, instances4env, volumes4env, volumeStorage4env, volumeSnapshots4env, volumeSnapStorage4env,
                floatingIp4env, securityGroups4env, securityGroupRules4env, keyPairs4env, serverGroups4env);
        out.println("\nResources validation for '" + MainClass.Commands.CREATE_ENV.getCommand().toUpperCase() + "' finished.\n");
        return res;
    }

    public boolean canCreateCluster(PrintStream out, Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
        out.println("\nResources validation for '" + MainClass.Commands.CREATE_CLUSTER.getCommand().toUpperCase() + "' started...\n");
        boolean res = canCreateValidation(out, config, novaApi, tenant, cinderApi, neutronApi,
                cores4newCluster, ram4newCluster, instances4newCluster, volumes4newCluster, volumeStorage4newCluster,
                volumeSnapshots4newCluster, volumeSnapStorage4newCluster, floatingIp4newCluster, securityGroups4newCluster,
                securityGroupRules4newCluster, keyPairs4newCluster, serverGroups4newCluster);
        out.println("\nResources validation for '" + MainClass.Commands.CREATE_CLUSTER.getCommand().toUpperCase() + "' finished.\n");
        return res;
    }

    public boolean canCreateAll(PrintStream out, Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi)
    {
        out.println("\nResources validation for '" + MainClass.Commands.CREATE_ALL.getCommand().toUpperCase() + "' started...\n");
        boolean res = canCreateValidation(out, config, novaApi, tenant, cinderApi, neutronApi,
                cores4env + cores4newCluster, ram4env + ram4newCluster, instances4env + instances4newCluster, volumes4env + volumes4newCluster,
                volumeStorage4env + volumeStorage4newCluster, volumeSnapshots4env + volumeSnapshots4newCluster,
                volumeSnapStorage4env + volumeSnapStorage4newCluster, floatingIp4env + floatingIp4newCluster, securityGroups4env + securityGroups4newCluster,
                securityGroupRules4env + securityGroupRules4newCluster, keyPairs4env + keyPairs4newCluster, serverGroups4env + serverGroups4newCluster);
        out.println("\nResources validation for '" + MainClass.Commands.CREATE_ALL.getCommand().toUpperCase() + "' finished.\n");
        return res;
    }

    private boolean canCreateValidation(PrintStream out, Configuration config, NovaApi novaApi, Tenant tenant, CinderApi cinderApi, NeutronApi neutronApi,
                              int coresRequired, int ramRequired, int instancesRequired, int volumesRequired, int volStorageRequired,
                              int volSnapshotsRequired, int volSnapStorageRequired, int floatingIpsRequired, int secGroupsRequired,
                              int secGroupRulesRequired, int keyPairsRequired, int serverGroupsRequired)
    {
        //loadHardwareStatsStatic(config, novaApi, tenant, cinderApi);
        this.loadHardwareStats(config, novaApi, tenant, cinderApi, neutronApi);
        List<String> errorSet = new ArrayList<String>();
        List<String> naSet = new ArrayList<String>();

        canCreateBuildErrorSet(errorSet, naSet, "Cores", coresMax, coresUsed, coresRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Ram (GB)", ramMax, ramUsed, ramRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Instances", instancesMax, instancesUsed, instancesRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Volumes (#)", volumesMax, volumesUsed, volumesRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Volumes (GB)", volumeStorageMax, volumeStorageUsed, volStorageRequired);
//        canCreateBuildErrorSet(errorSet, naSet, "Volume Snapshots (#)", volumeSnapshotsMax, volumeSnapshotsUsed, volSnapshotsRequired);
//        canCreateBuildErrorSet(errorSet, naSet, "Volume Snapshots (GB)", volumeSnapStorageMax, volumeSnapStorageUsed, volSnapStorageRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Floating IPs", floatingIpMax, floatingIpUsed, floatingIpsRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Security Groups", securityGroupsMax, securityGroupsUsed, secGroupsRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Security Group Rules", securityGroupRulesMax, securityGroupRulesUsed, secGroupRulesRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Key Pairs", keyPairsMax, keyPairsUsed, keyPairsRequired);
        canCreateBuildErrorSet(errorSet, naSet, "Server Groups", serverGroupsMax, serverGroupsUsed, serverGroupsRequired);

        if(errorSet.isEmpty())
        {
            out.println("No problems with resource availability detected.");
        }
        else
        {
            out.println("Operation could not be executed: NOT ENOUGH RESOURCES.\nERRORS:");
            for(String s : errorSet)
            {
                out.println(s);
            }
        }
        if(!naSet.isEmpty())
        {
            out.println("WARNINGS:");
            for(String s : naSet)
            {
                out.println(s);
            }
        }
        return errorSet.isEmpty();
    }

    private void canCreateBuildErrorSet(List<String> errorSet, List<String> naSet, String name, int max, int used, int required)
    {
        if(required > 0 && max == 0)
        {
            naSet.add(name + " ::: not possible to validate. Information about the resource was not received from the API.");
        }
        //else if(used > max)
        //{
        //    naSet.add(name + " ::: API seems to work incorrectly: used resources cannot be bigger than quota.");
        //}
        //else if((max - used) <= required)
        else if(!canCreateCheck(max, used, required))
        {
            errorSet.add(name + " ::: Quota: " + max + ", Available: " + (max - used) + ", Required: " + required + ".");
        }
    }
    
    private boolean canCreateCheck(int max, int used, int required)
    {
        return (max - used) > required;
    }

}
