package no.uit.metapipe.cpouta;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.jclouds.http.HttpResponseException;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.openstack.nova.v2_0.domain.*;
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
import java.nio.charset.StandardCharsets;
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
//                System.out.println("Removing.--");
//                keyPairApi.delete(config.getBastionKeyName());
            }
        }
        Utils.createNewKeyPair("id_rsa", sshFolder, ssh);
        Utils.localExecutor("sudo chmod 600 " + sshFolder + "/" + "id_rsa;", true);
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
                "mkvirtualenv --system-site-packages ansible2;" +
                "pip install --upgrade pip;" +
                "pip install setuptools;" +
                "pip install --upgrade setuptools;" +
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

//    static void clusterServerGroupCreate(Configuration config, final String u, final String p)
//    {
////        Authenticator.setDefault (new Authenticator() {
////            protected PasswordAuthentication getPasswordAuthentication() {
////                System.out.println(u + p);
////                return new PasswordAuthentication (u, p.toCharArray());
////            }
////        });
//        String urlParameters = "param1=a&param2=b&param3=c";
//        byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
//        int    postDataLength = postData.length;
//        String request        = "https://pouta.csc.fi:5001/v2.0/";
//        URL    url            = null;
//        HttpURLConnection conn = null;
//        try
//        {
//            url = new URL(request);
//            conn = (HttpURLConnection)url.openConnection();
//            conn.setDoOutput( true );
//            conn.setInstanceFollowRedirects(false);
////            conn.setRequestMethod("POST");
//            conn.setRequestMethod("GET");
//            conn.setRequestProperty("Authorization", "Basic " + u + ":" + p);
////        conn.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded");
////        conn.setRequestProperty( "charset", "utf-8");
////        conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
//            conn.setUseCaches(false);
////            DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
////            wr.write(postData);
//            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//            String line;
//            while ((line = rd.readLine()) != null)
//            {
////                result.append(line);
//                System.out.println(line);
//            }
//            rd.close();
//
//            conn.disconnect();
//        }
//        catch (MalformedURLException e)
//        {
//            e.printStackTrace();
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//        }
//
//
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
                    newLines.add(Utils.updateFileLine(line, "cluster_name", config.getClusterName()));
                    i++;
                }
                else if(line.contains("network_name:"))
                {
                    newLines.add(Utils.updateFileLine(line, "network_name", config.getNetworkName()));
                    i++;
                }
                else if(line.contains("ssh_key:"))
                {
                    newLines.add(Utils.updateFileLine(line, "ssh_key", config.getClusterKeyName()));
                    i++;
                }
                else if(line.contains("bastion_secgroup:"))
                {
                    newLines.add(Utils.updateFileLine(line, "bastion_secgroup", config.getBastionSecGroupName()));
                    i++;
                }
                else if(line.contains("master:"))
                {
                    newLines.add(line);
                    for(j = i+1; !line.contains("node_groups:"); )
                    {
                        line = lines.get(j);
                        if(line.contains("flavor:"))
                        {
                            newLines.add(Utils.updateFileLine(line, "flavor", config.getMaster().get("flavor")));
                            j++;
                        }
                        else if(line.contains("image:"))
                        {
                            newLines.add(Utils.updateFileLine(line, "image", config.getImageDefault()));
                            j++;
                        }
                        else if(line.contains("name: metadata"))
                        {
                            newLines.add(line);
                            line = lines.get(j+1);
                            newLines.add(Utils.updateFileLine(line, "size", config.getMaster().get("metadataVolumeSize")));
                            j = lines.indexOf(line) + 1;
                        }
                        else if(line.contains("name: nfs_share"))
                        {
                            newLines.add(line);
                            line = lines.get(j+1);
                            newLines.add(Utils.updateFileLine(line, "size", config.getMaster().get("nfsVolumeSize")));
                            i = lines.indexOf(line) + 1;
                            break;
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
                                newLines.add(Utils.updateFileLine(line, "flavor", config.getRegularHddNodes().get("flavor")));
                            }
                            else if(line.contains("image:"))
                            {
                                newLines.add(Utils.updateFileLine(line, "image", config.getImageDefault()));
                            }
                            else if(line.contains("num_vms:"))
                            {
                                newLines.add(Utils.updateFileLine(line, "num_vms", config.getRegularHddNodes().get("numNodes")));
                            }
                            else if(line.contains("name: datavol"))
                            {
                                newLines.add(line);
                                line = lines.get(++j);
                                newLines.add(Utils.updateFileLine(line, "size", config.getRegularHddNodes().get("volumeSize")));
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
                                newLines.add(Utils.updateFileLine(line, "flavor", config.getIoHddSsdNodes().get("flavor")));
                            }
                            else if(line.contains("image:"))
                            {
                                newLines.add(Utils.updateFileLine(line, "image", config.getImageDefault()));
                            }
                            else if(line.contains("num_vms:"))
                            {
                                newLines.add(Utils.updateFileLine(line, "num_vms", config.getIoHddSsdNodes().get("numNodes")));
                            }
                            else if(line.contains("name: datavol"))
                            {
                                newLines.add(line);
                                line = lines.get(++j);
                                newLines.add(Utils.updateFileLine(line, "size", config.getIoHddSsdNodes().get("hddVolumeSize")));
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
        System.out.println("Start transferring required files to Bastion...");
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
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVarsTemplate")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterSwSetupScript")) + ";" +
                "rm " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterSwSetupScriptInit")) + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        File arc = new File(tempFolder + "/arc.tar");
        DefaultFileSet fs = new DefaultFileSet();
        if(arc.exists())
        {
            arc.delete();
        }
        fs.setDirectory(new File("."));
        fs.setExcludes(new String[]{tempFolder + "/*", tempFolder});
        fs.setExcludes(new String[]{Utils.getFileNameFromPath(config.getXternFiles().get("installation")) + "/*",
                Utils.getFileNameFromPath(config.getXternFiles().get("installation"))});
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
        }
        Utils.sshCopier(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()),
                new String[]{arc.getAbsolutePath()}, "");
        commands = "tar -xf arc.tar --overwrite;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Required files are transferred to Bastion.");
    }

    static void updateInstallationBashScripts(JSch ssh, Configuration config, Server bastion, Server server,
                                              boolean bastion1master0, VolumeApi volumeApi, VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Updating installation scripts on " + server.getName() + "...");
        String commands = "";
        String ip;
        String src;
        String dest;
        attachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        if(bastion1master0)
        {
            mountSwDisk(ssh, config, bastion);
            ip = Utils.getServerPublicIp(server, config.getNetworkName());
            dest = "/media/" + config.getSwDiskName() + "/" + config.getInstallationPackedLocation();
            src = Utils.getFileNameFromPath(config.getXternFiles().get("installation"));
        }
        else
        {
            mountSwDisk(ssh, config, null);
            ip = Utils.getServerPrivateIp(server, config.getNetworkName());
            dest = config.getInstallationUnpackedLocation();
            src = "/media/" + config.getSwDiskName() + "/" + config.getInstallationPackedLocation();
        }
        commands += "sudo rm -r " + dest + "/*.sh;";
        Utils.sshExecutor(ssh, config.getUserName(), ip, commands);
        System.out.println("Start copying...");
        System.out.println(src + " " + dest);
        for(String f : new File(src).list())
        {
            if(!new File(f).isDirectory() && f.endsWith(".sh"))
            {
                Utils.sshCopier(ssh, config.getUserName(), ip,
                        new String[]{src + "/" + f}, dest.substring(1), false, true);
            }
        }
        if(bastion1master0)
        {
            unmountSwDisk(ssh, config, bastion);
        }
        else
        {
            unmountSwDisk(ssh, config, null);
        }
        detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        commands = "sudo chmod 777 " + dest + "/*.sh;";
        Utils.sshExecutor(ssh, config.getUserName(), ip, commands);
        System.out.println("Installation scripts are updated on " + server.getName());
    }

    static void bastionClusterProvisionExecute(JSch ssh, Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Start executing cluster provision routine on Bastion...");
        String commands = authCommands +
                "source ~/.virtualenvs/ansible2/bin/activate;" +
                "export ANSIBLE_HOST_KEY_CHECKING=False;" +
                "ansible-playbook -v -e @" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                    " ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/hortonworks/provision.yml;" +
                "ansible-playbook -v -i hortonworks-inventory -e @" +
                Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                " ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/common/wait_for_ssh.yml;" +
                "deactivate;" +
                "echo 'Cluster provision finished.';" +
                "sleep 1;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Cluster provision routine on Bastion is finished.");
    }

    static void bastionClusterConfigurationExecute(JSch ssh, Configuration config, Server bastion, String authCommands)
    {
        System.out.println("Start executing cluster configuration routine on Bastion...");
        String commands = authCommands +
                "source ~/.virtualenvs/ansible2/bin/activate;" +
                "export ANSIBLE_HOST_KEY_CHECKING=False;" +
                "ansible-playbook -v -i hortonworks-inventory -e @" +
                Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars")) +
                " ~/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleScript")) +
                "/playbooks/hortonworks/configure.yml;" +
                "echo 'Provisioned cluster configuration finished.';" +
                "sleep 1;" +
                "deactivate;";
        System.out.println(commands);
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("Cluster configuration routine on Bastion is finished.");
    }

    static void createDisk4SW(JSch ssh, Configuration config, VolumeApi volumeApi, Server bastion,
                              VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Started creating installation storage...");
        Volume vol;
        String commands;
        for(Volume v : volumeApi.listInDetail())
        {
            if(v.getName().equals(config.getSwDiskName()))
            {
                System.out.println("Volume with the name " + config.getSwDiskName() + " already exists. " +
                        "If it is a mistake, check the name in config.yml and rerun the tool.");
                return;
            }
        }
        vol = volumeApi.create(config.getSwDiskSize(),
                CreateVolumeOptions.Builder.name(config.getSwDiskName()).availabilityZone(config.getBastionAvailZone()));
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
                " " + config.getSwDiskID() + " create " + config.getSwDiskName() + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println("Finished creating installation storage with the name '" + config.getSwDiskName() + "'.");
    }

    static VolumeAttachment attachSwDisk(Configuration config, Server server, VolumeApi volumeApi,
                                                 VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Attaching swDisk to " + server.getName() + "...");
        VolumeAttachment va;
        if(volumeAttachmentApi.getAttachmentForVolumeOnServer(config.getSwDiskID(), server.getId()) != null)
        {
            System.out.println("Disk is already attached to " + server.getName());
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
        System.out.println("Detaching swDisk to " + server.getName() + "...");
        VolumeAttachment va = volumeAttachmentApi.getAttachmentForVolumeOnServer(config.getSwDiskID(), server.getId());
        if(va == null)
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

    static void mountSwDisk(JSch ssh, Configuration config, Server server)
    {
        System.out.println("Start mounting swDisk...");
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("diskHelper")) +
                " " + config.getSwDiskID() + " mount " + config.getSwDiskName() + " 2>&1;";
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
        System.out.println("swDisk mounted on /media/" + config.getSwDiskName() + "/");
    }

    static void unmountSwDisk(JSch ssh, Configuration config, Server server)
    {
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("diskHelper")) +
                " " + config.getSwDiskID() + " unmount " + config.getSwDiskName() + " 2>&1;";
        System.out.println("Start unmounting swDisk from /media/" + config.getSwDiskName() + "/");
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
        System.out.println("swDisk unmounted from /media/" + config.getSwDiskName() + "/");
    }

    static void transferInstallationFiles2VDisk(JSch ssh, Configuration config, Server bastion, boolean replace)
    {
        String commands = "";
        String swPath = "/media/" + config.getSwDiskName() + "/" + config.getInstallationPackedLocation();
        List<String> swFiles = new ArrayList<String>();
        for(File f : new File(config.getXternFiles().get("installation")).listFiles())
        {
            swFiles.add(f.getAbsolutePath());
        }
        System.out.println("Start transferring installation files to swDisk...");
        mountSwDisk(ssh, config, bastion);
        if(replace)
        {
            commands += "sudo rm -r " + swPath + ";";
        }
        commands += "sudo mkdir " + swPath + ";" +
                "sudo chmod 777 " + swPath + ";";
        System.out.println();
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        Utils.sshCopier(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()),
                swFiles.toArray(new String[swFiles.size()]), swPath.substring(1), false, replace);
        commands =
                "sudo chmod 777 -R " + swPath + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        unmountSwDisk(ssh, config, bastion);
        System.out.println("Installation files are transferred to swDisk.");
    }

    static void openAdminAccess(Configuration config, SecurityGroupApi securityGroupApi)
    {
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
        MainClass.addIpMasterAccess(Arrays.asList(myIP), securityGroupApi);
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
        writer.println("url=\"" + masterPublicIp + ":4040\"");
        writer.println("firefox $url");
        writer.close();
        f.setExecutable(true);
        System.out.println("URL-link to Ambari cluster management generated: " + f.getName());
    }

}
