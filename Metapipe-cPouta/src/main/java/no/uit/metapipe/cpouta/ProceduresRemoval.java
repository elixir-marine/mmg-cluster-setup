package no.uit.metapipe.cpouta;

import com.google.common.collect.FluentIterable;
import com.jcraft.jsch.JSch;
import org.jclouds.openstack.nova.v2_0.domain.SecurityGroup;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerGroup;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.KeyPairApi;
import org.jclouds.openstack.nova.v2_0.extensions.SecurityGroupApi;
import org.jclouds.openstack.nova.v2_0.extensions.ServerGroupApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;

import java.io.File;

import static java.lang.Thread.sleep;

/**
 * Created by alexander on 8/26/16.
 */
class ProceduresRemoval
{

    private ProceduresRemoval() { }

    static void deleteCluster(JSch ssh, ServerGroupApi serverGroupApi, Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Starting cluster removing procedure...");
        String commands = authCommands +
                "source ~/.virtualenvs/ansible2/bin/activate;" +
                "ansible-playbook -v ~/pouta-ansible-cluster/playbooks/hortonworks/deprovision.yml" +
                " -e cluster_name=" + config.getClusterName() +
                " -e num_nodes="  + config.getNumNodes() +
                " -e remove_master=1 -e remove_master_volumes=1 -e remove_nodes=1 -e remove_node_volumes=1" +
                " -e remove_security_groups=1";
//        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Cluster instances are terminated.");
        if(config.getServerGroupId() != null && !config.getServerGroupId().isEmpty())
        {
            serverGroupApi.delete(config.getServerGroupId());
            FluentIterable<ServerGroup> tempList = serverGroupApi.list();
            boolean found = false;
            for(int i = 0; tempList.size() != 0; i = (i + 1) % tempList.size())
            {
                if(tempList.get(i).getId().equals(config.getServerGroupId()))
                {
                    found = true;
                }
                if(i == tempList.size() - 1)
                {
                    if(found == false)
                    {
                        break;
                    }
                    // wait for the group to get removed from the list
                    try {
                        sleep(1000);
                        tempList = serverGroupApi.list();
                        found = false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            config.setAndUpdateServerGroupId("");
            System.out.println("Cluster server group is removed.");
        }
        (new File("ambari_" + config.getClusterName() + "_firefox.sh")).delete();
        System.out.println("Cluster is removed.");
    }

    static void removeBastion(Configuration config, ServerApi serverApi, FloatingIPApi floatingIPApi, Server bastion)
    {
        System.out.println("Starting Bastion removing procedure...");
        serverApi.delete(bastion.getId());
        while(serverApi.get(bastion.getId()) != null)
        {
            // wait for bastion to get deleted
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        floatingIPApi.delete(config.getBastionFloatingIpId());
        config.setAndUpdateBastionFloatingIpId("");
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
                    // wait for the group to get removed from the list
                    try {
                        sleep(1000);
                        tempList = securityGroupApi.list();
                        found = false;
                    } catch (InterruptedException e) {
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
                        try {
                            sleep(1000);
                            tempList = keyPairApi.list();
                            found = false;
                        } catch (InterruptedException e) {
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
                        try {
                            sleep(1000);
                            tempList = keyPairApi.list();
                            found = false;
                        } catch (InterruptedException e) {
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
