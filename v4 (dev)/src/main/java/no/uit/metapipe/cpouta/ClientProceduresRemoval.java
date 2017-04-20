package no.uit.metapipe.cpouta;

import com.google.common.collect.FluentIterable;
import com.jcraft.jsch.JSch;
import org.jclouds.http.HttpResponseException;
import org.jclouds.openstack.cinder.v1.features.SnapshotApi;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.SecurityGroup;
import org.jclouds.openstack.nova.v2_0.domain.Server;
//import org.jclouds.openstack.nova.v2_0.domain.ServerGroup;
import org.jclouds.openstack.nova.v2_0.extensions.*;
//import org.jclouds.openstack.nova.v2_0.extensions.ServerGroupApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

import java.io.File;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;



class ClientProceduresRemoval
{
    private ClientProceduresRemoval() { }

    static void deleteCluster(JSch ssh, /*ServerGroupApi serverGroupApi, */Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Starting cluster removing procedure...");
        String commands;
        commands =
                "source ~/.virtualenvs/ansible-2.3/bin/activate;" +
                "ansible-playbook -e @" +
                    Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                    " -v ~/pouta-ansible-cluster/playbooks/hortonworks/deprovision.yml" +
                " -e cluster_name=" + config.getClusterName() +
                " -e num_nodes="  + Integer.toString(Integer.parseInt(config.getRegularHddNodes().get("numNodes")) +
                    Integer.parseInt(config.getIoHddSsdNodes().get("numNodes"))) +
                " -e remove_masters=1 -e remove_master_volumes=1 -e remove_nodes=1 -e remove_node_volumes=1" +
                " -e remove_security_groups=1 2>&1;" +
                "deactivate;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), authCommands + commands);
        System.out.println("Cluster instances are terminated.");
//        if(config.getServerGroupId() != null && !config.getServerGroupId().isEmpty())
//        {
//            serverGroupApi.delete(config.getServerGroupId());
//            FluentIterable<ServerGroup> tempList = serverGroupApi.list();
//            boolean found = false;
//            for(int i = 0; tempList.size() != 0; i = (i + 1) % tempList.size())
//            {
//                if(tempList.get(i).getId().equals(config.getServerGroupId()))
//                {
//                    found = true;
//                }
//                if(i == tempList.size() - 1)
//                {
//                    if(found == false)
//                    {
//                        break;
//                    }
//                    try
//                    {
//                        sleep(1000);
//                        tempList = serverGroupApi.list();
//                        found = false;
//                    }
//                    catch (InterruptedException e)
//                    {
//                        e.printStackTrace();
//                    }
//                }
//            }
//            config.setAndUpdateServerGroupId("");
//            System.out.println("Cluster server group is removed.");
//        }
        (new File(config.getClusterName() + "_firefox.sh")).delete();
        System.out.println("Cluster is removed.");
    }

    static void removeSwCaches(JSch ssh, Configuration config, VolumeApi volumeApi, VolumeAttachmentApi volumeAttachmentApi,
                               Server bastion, boolean force)
    {
        System.out.println("Started removing SW...");
        if(!Utils.objectHasContents(config.getSwDiskID()) || volumeApi.get(config.getSwDiskID()) == null)
        {
            System.out.println("SW storage not found.");
            config.setAndUpdateSwDiskID("");
            return;
        }
        if(!force)
        {
            ClientProcedures.unmountSwDisk(ssh, config, bastion);
        }
        ClientProcedures.detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println("Removing...");
        try
        {
            volumeApi.delete(config.getSwDiskID());
            while(volumeApi.get(config.getSwDiskID()) != null)
            {
                sleep(1000);
            }
            System.out.println("SW successfully removed.");
        }
        catch (HttpResponseException e)
        {
            e.printStackTrace();
            if(e.toString().contains("500 Internal Server Error"))
            {
                System.out.println("\nSW storage was not removed due to Internal Server Error.\n" +
                        "The operation is continued, but the storage should be removed manually later.\n");
            }
            else
            {
                exit(0);
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
            exit(0);
        }
        config.setAndUpdateSwDiskID("");
    }

    static void removeBastion(Configuration config, ServerApi serverApi, FloatingIPApi floatingIPApi, Server bastion)
    {
        System.out.println("Starting Bastion removing procedure...");
        FloatingIP f = floatingIPApi.get(config.getBastionFloatingIpId());
        if(bastion != null)
        {
            floatingIPApi.removeFromServer(f.getIp(), bastion.getId());
            serverApi.delete(bastion.getId());
            while(serverApi.get(bastion.getId()) != null)
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
        }
        if(config.getBastionFloatingIpId() != null)
        {
            try
            {
                floatingIPApi.delete(config.getBastionFloatingIpId());
            }
            catch(HttpResponseException e)
            {
                e.printStackTrace();
            }
            config.setAndUpdateBastionFloatingIpId("");
        }
        System.out.println("Bastion is removed.");
    }

    static void removeOsSetups(Configuration config, SecurityGroupApi securityGroupApi, KeyPairApi keyPairApi)
    {
        System.out.println("Starting csc-setups removing procedure...");
        FluentIterable tempList;
        boolean found;
        if(config.getSecurityGroupId() != null && !config.getSecurityGroupId().isEmpty())
        {
            securityGroupApi.delete(config.getSecurityGroupId());
            tempList = securityGroupApi.list();
            found = false;
            for(int i = 0; tempList.size() != 0; i = (i + 1) % tempList.size())
            {
                if(((SecurityGroup)tempList.get(i)).getId().equals(config.getSecurityGroupId()))
                {
                    found = true;
                }
                if(i == tempList.size() - 1)
                {
                    if(found == false)
                    {
                        break;
                    }
                    try
                    {
                        sleep(1000);
                        tempList = securityGroupApi.list();
                        found = false;
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            config.setAndUpdateSecurityGroupId("");
            System.out.println("Bastion security group is removed.");
        }
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : keyPairApi.list())
        {
            if(k.getName().equals(config.getClusterKeyName()))
            {
                keyPairApi.delete(config.getClusterKeyName());
                tempList = keyPairApi.list();
                found = false;
                for(int i = 0; tempList.size() != 0; i = (i + 1) % tempList.size())
                {
                    if(((org.jclouds.openstack.nova.v2_0.domain.KeyPair)tempList.get(i)).getName().
                            equals(config.getClusterKeyName()))
                    {
                        found = true;
                    }
                    if(i == tempList.size() - 1)
                    {
                        if(found == false)
                        {
                            break;
                        }
                        try
                        {
                            sleep(1000);
                            tempList = keyPairApi.list();
                            found = false;
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("Cluster keypair is removed.");
                break;
            }
        }
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : keyPairApi.list())
        {
            if(k.getName().equals(config.getBastionKeyName()))
            {
                keyPairApi.delete(config.getBastionKeyName());
                tempList = keyPairApi.list();
                found = false;
                for(int i = 0; tempList.size() != 0; i = (i + 1) % tempList.size())
                {
                    if(((org.jclouds.openstack.nova.v2_0.domain.KeyPair)tempList.get(i)).getName().
                            equals(config.getBastionKeyName()))
                    {
                        found = true;
                    }
                    if(i == tempList.size() - 1)
                    {
                        if(found == false)
                        {
                            break;
                        }
                        try
                        {
                            sleep(1000);
                            tempList = keyPairApi.list();
                            found = false;
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("Bastion keypair is removed.");
                break;
            }
        }
    }

}
