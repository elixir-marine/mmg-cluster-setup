package no.uit.metapipe.cpouta;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.openstack.nova.v2_0.domain.*;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.KeyPairApi;
import org.jclouds.openstack.nova.v2_0.extensions.SecurityGroupApi;
import org.jclouds.openstack.nova.v2_0.extensions.ServerGroupApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;

/**
 * Created by alexander on 8/26/16.
 */
class Procedures
{

    private Procedures() { }

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
//                bastionSecGroupObject = temp;
                return;
//                System.out.println("Removing and re-creating.");
//                securityGroupApi.delete(temp.getId());
//                for(int j = i; j < tempList.size(); j++)
//                {
//                    temp = (SecurityGroup)tempList.get(j);
//                    if(temp.getName().equals(config.getBastionSecGroupName()))
//                    {
//                        securityGroupApi.delete(temp.getId());
//                    }
//                }
//                break;
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
                "129.242.0.0/16");
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
        String privateKey = sshFolder + "/id_rsa";
        String pubKey = sshFolder + "/id_rsa.pub";
        File privateKeyFile = new File(privateKey);
        File pubKeyFile = new File(pubKey);
        FluentIterable<org.jclouds.openstack.nova.v2_0.domain.KeyPair> tempList = keyPairApi.list();
        for(org.jclouds.openstack.nova.v2_0.domain.KeyPair k : tempList)
        {
            if(k.toString().contains(bastionKeyName))
            {
                System.out.println("Found already existing openstack keypair.");
                return;
//                System.out.println("Removing.--");
//                keyPairApi.delete(config.getBastionKeyName());
            }
        }
        if(new File(privateKey).exists() && new File(pubKey).exists())
        {
            System.out.println("id_rsa ssh keys already exist.");
        }
        else
        {

            System.out.println(privateKey);
            System.out.println("Generating new keys...");
            try {
                if(!new File(sshFolder).exists())
                {
                    Files.createDirectories(Paths.get(sshFolder));
                }
                KeyPair keyPair = KeyPair.genKeyPair(ssh, KeyPair.RSA);
                privateKeyFile.createNewFile();
                pubKeyFile.createNewFile();
                keyPair.writePrivateKey(privateKey);
                keyPair.writePublicKey(pubKey, "");
                privateKeyFile.setReadable(true, true);
                privateKeyFile.setWritable(true, true);
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
        }
        System.out.println("Adding the key pair to Openstack...");
        try {
            keyPairApi.createWithPublicKey(bastionKeyName, new String(Files.readAllBytes(Paths.get(pubKey))));
        } catch (IOException e) {
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

    static Server bastionServerCreate(ServerApi serverApi, ImageApi imageApi, FlavorApi flavorApi, Configuration config)
    {
        System.out.println("Start creating bastion server.");
        ImmutableList<Server> tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            if(s.getName().equals(config.getBastionMachineName()))
            {
                System.out.println("Bastion server already exists.");
                return s;
//                exit(1);
//                System.out.println("Removing...");
//                serverApi.delete(s.getId());
//                while(serverApi.get(s.getId()) != null)
//                {
//                    // wait for bastion to get deleted
//                    try {
//                        sleep(1000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
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
            if(i.getName().equals(config.getBastionImage()))
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
            try {
                sleep(1000);
            } catch (InterruptedException e) {
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
        String commands = "sudo yum install -y dstat lsof bash-completion time tmux git xauth screen nano vim bind-utils nmap-ncat git xauth firefox centos-release-openstack  python-novaclient python-openstackclient python-devel python-setuptools python-virtualenvwrapper libffi-devel openssl-devel java-1.8.0-openjdk;" +
                "sudo yum groupinstall -y 'Development Tools';" +
                "sudo yum update -y;" +
                "sudo reboot;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Waiting for Bastion to restart...");
        while(serverApi.get(bastion.getId()).getStatus() != Server.Status.ACTIVE)
        {
            // wait for bastion to restart
            try {
                sleep(1000);
            } catch (InterruptedException e) {
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
                "mkvirtualenv --system-site-packages ansible2;" +
                "pip install --upgrade pip setuptools;" +
                "pip install ansible==2.1;" +
                "pip install shade dnspython funcsigs functools32";
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

    static void clusterServerGroupCreate(Configuration config, ServerGroupApi serverGroupApi)
    {
        if(config.getServerGroupId() != null)
        {
            System.out.println("Cluster server group already registered, no need to create a new one.");
            return;
        }
        System.out.println("Start creating cluster server group.");
        FluentIterable<ServerGroup> tempList;
        String groupName = config.getClusterName() + "-common";
        ServerGroup temp = serverGroupApi.create(groupName, Arrays.asList("anti-affinity"));
        int i = 0;
        config.setAndUpdateServerGroupId(temp.getId());
        tempList = serverGroupApi.list();
        while(!((ServerGroup)tempList.get(i)).getId().equals(config.getServerGroupId()))
        {
            if(i == tempList.size() - 1)
            {
                // wait for the group to appear in the list
                try {
                    sleep(1000);
                    tempList = serverGroupApi.list();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            i = (i + 1) % tempList.size();
        }
        System.out.println("'" + groupName + "' server group created. Server group list:");
        for(ServerGroup s : tempList)
        {
            System.out.println(s);
        }
    }

    static void createClusterVarsFile(Configuration config, String tempFolder)
    {
        System.out.println("Generating 'cluster_vars.yaml'...");
        String filePath = tempFolder + "/cluster_vars.yaml";
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
        writer.println("cluster_name: " + config.getClusterName());
        writer.println("master_flavor: " + config.getMasterFlavor());
        writer.println("node_flavor: " + config.getNodeFlavor());
        writer.println("num_nodes: " + config.getNumNodes());
        writer.println("ssh_key: " + config.getClusterKeyName());
        writer.println("network_name: " + config.getNetworkName());
        writer.println("bastion_secgroup: " + config.getBastionSecGroupName());
        writer.println("master_volume_size: " + config.getMasterVolumeSize());
        writer.println("node_volume_size: " + config.getNodeVolumeSize());
        writer.println("data_volume_type: " + config.getVolumeType());
        writer.close();
        System.out.println("'cluster_vars.yaml' generated:");
        try {
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            String line = null;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void updateAmbariShellCommandFile(Configuration config)
    {
        System.out.println("Generating/updating " + config.getXternFiles().get("ambariShellCommands") + "...");
        File file = new File(config.getXternFiles().get("ambariShellCommands"));
        String passCommand = "users changepassword --user admin --oldpassword admin --newpassword " + config.getAmbariPassword() + " --adminuser true";
        if(!file.exists())
        {
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(file.getAbsoluteFile(), "UTF-8");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                exit(1);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                exit(1);
            }
            writer.println(passCommand);
            writer.close();
        }
        else
        {
            try
            {
                List<String> newLines = new ArrayList<String>();
                List<String> lines = Files.readAllLines(file.toPath(), Charset.defaultCharset());
                for(String line: lines)
                {
                    if(line.contains("users changepassword"))
                    {
                        newLines.add(passCommand);
                    }
                    else
                    {
                        newLines.add(line);
                    }
                }
                Files.write(file.toPath(), newLines, Charset.defaultCharset());
            }
            catch (IOException e)
            {
                e.printStackTrace();
                exit(1);
            }
        }
        System.out.println(config.getXternFiles().get("ambariShellCommands") + ":");
        try {
            BufferedReader br = new BufferedReader(new FileReader(file.getAbsoluteFile()));
            String line = null;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void transferRequiredFiles2Bastion(JSch ssh, Configuration config, Server bastion, String tempFolder)
    {
        System.out.println("Start transferring required files to bastion...");
        TarArchiver aTar = new TarArchiver();
        String commands;
        commands =
                "rm arc.tar;" +
                        "rm -r " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) + ";" +
                        "rm -r temp;" +
                        "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ambariShellJar")) + ";" +
                        "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ambariShellCommands")) + ";" +
                        "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterTestScript")) + ";" +
                        "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("thisJar")) + ";" +
                        "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("config")) + ";" +
                        "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        File arc = new File(tempFolder + "/arc.tar");
        DefaultFileSet fs = new DefaultFileSet();
        if(arc.exists())
        {
            arc.delete();
        }
        fs.setDirectory(new File("."));
        fs.setExcludes(new String[]{"temp/*", "temp"});
        fs.setIncludingEmptyDirectories(true);
        aTar.addFileSet(fs);
        aTar.addFile(new File(config.getXternFiles().get("ansibleClusterVars")),
                Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")));
        aTar.setDestFile(arc);
        try
        {
            aTar.createArchive();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        Utils.sshCopier(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), new String[]{arc.getAbsolutePath()}, "");
        commands = "tar -xvf arc.tar --overwrite;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Required files are transferred to bastion.");
    }

    static void bastionClusterProvisionExecute(JSch ssh, Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Start executing cluster provision routine on Bastion...");
        String commands = authCommands +
                "source ~/.virtualenvs/ansible2/bin/activate;" +
                "export ANSIBLE_HOST_KEY_CHECKING=False;" +
                "ansible-playbook -v -e @cluster_vars.yaml ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/hortonworks/provision.yml;" +
                "echo 'Cluster provision finished.';" +
                "sleep 3;" +
                "ansible-playbook -v -i hortonworks-inventory ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/hortonworks/configure.yml;" +
                "echo 'Provisioned cluster configuration finished.';" +
                "sleep 1;" +
                "deactivate;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Cluster provision routine on Bastion is finished.");
    }

    static void openAdminAccess(Configuration config, SecurityGroupApi securityGroupApi)
    {
        List<String> ips;
        String myIP = "";
        BufferedReader in = null;
        try
        {
            in = new BufferedReader(new InputStreamReader(new URL(config.getIpCheck()).openStream()));
            myIP = in.readLine();
        }
        catch (IOException e)
        {
            System.out.println(config.getIpCheck() + "doesn't seem to work correctly!");
            e.printStackTrace();
            exit(1);
        }
        System.out.println("Your public IP address: " + myIP);
        config.setAndUpdateIpAdmins(Arrays.asList(myIP));
        ips = config.getIpAdmins();
        for(String ip : ips)
        {
            MainClass.addIpMasterAccess(ip, securityGroupApi);
        }
    }

    static void generateAmbariLink(String clusterName, String masterPublicIp)
    {
        File f = new File("./ambari_" + clusterName + "_firefox.sh");
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
        writer.println("url=\"" + masterPublicIp + ":8080\"");
        writer.println("firefox $url");
        writer.close();
        f.setExecutable(true);
        System.out.println("URL-link to Ambari cluster management generated: " + f.getName());
    }

}
