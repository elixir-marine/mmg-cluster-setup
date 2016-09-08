package no.uit.metapipe.cpouta;

import com.google.common.collect.*;
import com.google.common.io.Closeables;
import com.google.inject.Module;
import com.jcraft.jsch.*;
import com.jcraft.jsch.KeyPair;
import jline.console.ConsoleReader;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.openstack.glance.v1_0.options.ListImageOptions;
import org.jclouds.openstack.keystone.v2_0.config.CredentialTypes;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.*;
import org.jclouds.openstack.nova.v2_0.extensions.*;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.rest.AuthorizationException;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;



public class MainClass implements Closeable
{

    private static Configuration config = null;

    private static String testString = "test";

    private String userName;
    private String password;

    private String tempFolder;
    private File tempFolderFile;

    private NovaApi novaApi;
    private Set<String> regions;
    private SecurityGroupApi securityGroupApi;
    private ServerApi serverApi;
    private FlavorApi flavorApi;
    private ImageApi imageApi;
    private KeyPairApi keyPairApi;
    private FloatingIPApi floatingIPApi;
    private ServerGroupApi serverGroupApi;

    private Server bastion;

    private JSch ssh;
    private String sshFolder = System.getProperty("user.home") + "/.ssh";

    private String cscAuthOnBastionCommands;

    private enum Commands
    {
        HELP("help"),
        QUIT("quit"),
        EXIT("exit"),
        CREATE_ENV("create-env"),
        CREATE_CLUSTER("create-cluster"),
        CREATE_ALL("create-all"),
        TEST("test"),
        REMOVE_CLUSTER("remove-cluster"),
        REMOVE_ALL("remove-all"),
        SCALE_UP("scale-up"),
        SCALE_DOWN("scale-down"),
        IP_ADMIN_ADD("ip-admin-add"),
        IP_ADMIN_REMOVE("ip-admin-remove");
        private final String command;
        Commands(String command)
        {
            this.command = command;
        }
        public String getCommand()
        {
            return this.command;
        }
        public static String[] getCommands()
        {
            Commands[] c = values();
            String[] cs = new String[c.length];
            for (int i = 0; i < c.length; i++)
            {
                cs[i] = c[i].getCommand();
            }
            return cs;
        }
    }

    // RUN JAR IN TERMINAL: java -jar $(pwd)/Metapipe-cPouta.jar
    private MainClass(String userName, String password, String idRsaKeyFileName)
    {
        System.out.print("Starting... \n\n");

        this.userName = userName;
        this.password = password;

        cscAuthOnBastionCommands = "export OS_AUTH_URL=" + config.getOsAuthName() + ";" +
                "export OS_TENANT_NAME=" + config.getProjectName() + ";" +
                "export OS_USERNAME=" + userName + ";" +
                "export OS_PASSWORD=" + password + ";" +
                "export OS_REGION_NAME=" + config.getRegionName() + ";";

        ssh = new JSch();
        if(idRsaKeyFileName != null)
        {
            File key = new File(sshFolder + "/" + idRsaKeyFileName);
            if(!key.exists())
            {
                Utils.createNewKeyPair(idRsaKeyFileName, sshFolder, ssh);
            }
            try
            {
                ssh.addIdentity(sshFolder + "/" + idRsaKeyFileName);
            }
            catch (JSchException e)
            {
                e.printStackTrace();
                exit(1);
            }
        }

        Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());

        Properties overrides = new Properties();
        overrides.setProperty(KeystoneProperties.CREDENTIAL_TYPE, CredentialTypes.PASSWORD_CREDENTIALS);
        overrides.setProperty(Constants.PROPERTY_API_VERSION, "2");
        overrides.setProperty(Constants.PROPERTY_LOGGER_WIRE_LOG_SENSITIVE_INFO, "true");
        overrides.setProperty(KeystoneProperties.TENANT_NAME, config.getProjectName());

        if(this.userName != null && this.password != null)
        {
            novaApi = ContextBuilder.newBuilder(new NovaApiMetadata())
                    .endpoint(config.getOsAuthName())
                    .credentials(userName, password)
                    .modules(modules)
                    .overrides(overrides)
                    .buildApi(NovaApi.class);

            regions = novaApi.getConfiguredRegions();
            securityGroupApi = novaApi.getSecurityGroupApi(config.getRegionName()).get();
            serverApi = novaApi.getServerApi(config.getRegionName());
            flavorApi = novaApi.getFlavorApi(config.getRegionName());
            imageApi = novaApi.getImageApi(config.getRegionName());
            keyPairApi = novaApi.getKeyPairApi(config.getRegionName()).get();
            floatingIPApi = novaApi.getFloatingIPApi(config.getRegionName()).get();
            serverGroupApi = novaApi.getServerGroupApi(config.getRegionName()).get();
        }

        String path = MainClass.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = "";
        try {
            decodedPath = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            exit(1);
        }
        tempFolder = (new File("temp")).getAbsolutePath();
        tempFolderFile = new File(tempFolder);
        if(!tempFolderFile.exists())
        {
            tempFolderFile.mkdir();
        }
        System.out.print("\nTemporary folder: " + tempFolder + "\n\n");
    }

    public static void main(String[] args)
    {
        MainClass mainClass;
        System.out.println();
        Yaml yaml = new Yaml();
        final String programName = new java.io.File(MainClass.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();
        final String helpLaunch =
                "Required arguments:\n" +
                "username=CSC_Username " +
                "password=CSC_Password \n";
        final String help =
                "BE SURE THAT CONFIG.YML IS CONFIGURED CORRECTLY.\n";
        final String helpOps =
                "Commands:\n" +
                "help: This message.\n" +
                "quit/exit: Exit.\n" +
                "create-env: Create the environment from scratch - all CSC settings, bastion host \n" +
                "create-cluster: Create only a new cluster " +
                    "(given that 'create-env' was executed before, and cluster doesn't exist).\n" +
                "create-all: 'create-env' + 'create-cluster'\n" +
                "test: Run test on the existing cluster.\n" +
                "remove-cluster: Remove existing cluster and keep the CSC environment for future use.\n" +
                "remove-all: Remove existing cluster, environment, cleanup everything.\n" +
                "ip-admin-add <X.X.X.X>: Adding IP to admins will give the IP-owner access to Ambari cluster management web-gui.\n" +
                "ip-admin-remove <X.X.X.X>: Remove the IP from admins.\n";
//                "(not implemented yet) scale-up X: Spawns and adds X number of new instances to the existing cluster.\n" +
//                "(not implemented yet) scale-down X: Scales down existing cluster with X number of instances.\n";
        String usr = null, pswd = null;

        try
        {
            InputStream in = Files.newInputStream(Paths.get("config.yml"));
            config = yaml.loadAs(in, Configuration.class );
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        if(args.length == 0)
        {
            System.out.println(helpLaunch);
        }
        else
        {
            for(String arg : args)
            {
                if(arg.trim().contains("username="))
                {
                    usr = arg.trim().replace("username=", "");
                }
                if(arg.trim().contains("password="))
                {
                    pswd = arg.trim().replace("password=", "");
                }
            }
            for(String arg : args)
            {
                if(arg.trim().equals("_bastion-routine"))
                {
                    System.out.println("===== BASTION ROUTINE =====");
                    mainClass = new MainClass(usr, pswd, config.getClusterKeyFileName());
                    mainClass.bastionRoutine(Arrays.asList(args).contains(testString));
                    return;
                }
                else if(arg.trim().equals("_master-routine"))
                {
                    System.out.println("===== MASTER ROUTINE =====");
                    mainClass = new MainClass(usr, pswd, null);
                    mainClass.masterRoutine(Arrays.asList(args).contains(testString));
                    return;
                }
            }
        }
        InputStream inStream = new FileInputStream(FileDescriptor.in);
        ConsoleReader reader;
        try
        {
            reader = new ConsoleReader(programName, inStream, System.out, null);
            reader.setPrompt("> ");
            reader.addCompleter(new FileNameCompleter());
            reader.addCompleter(new StringsCompleter(Arrays.asList(Commands.getCommands())));
            String line;
            PrintWriter out = new PrintWriter(reader.getOutput());
            if(usr == null || pswd == null || usr.isEmpty() || pswd.isEmpty())
            {
                out.println(helpLaunch);
                while(true)
                {
                    out.println("Enter openstack username:");
                    usr = reader.readLine();
                    if(usr.isEmpty())
                    {
                        out.println("Username can't be empty!");
                        continue;
                    }
                    out.println();
                    out.println("Enter openstack password:");
                    reader.setEchoCharacter(new Character('*'));
                    pswd = reader.readLine();
                    if(pswd.isEmpty())
                    {
                        out.println("Password can't be empty!");
                        continue;
                    }
                    reader.setEchoCharacter(null);
                    try
                    {
                        ContextBuilder.newBuilder(new NovaApiMetadata())
                                .endpoint(config.getOsAuthName())
                                .credentials(usr, pswd)
                                .buildApi(NovaApi.class).getConfiguredRegions();
                        break;
                    }
                    catch (AuthorizationException e)
                    {
                        out.println("\nLogin failed, check the correctness of username and password, and try again.\n");
                    }
                    catch (Exception e)
                    {
                        break;
                    }
                }
                out.println("\nLogged in successfully.\n");
            }
            out.println(help);
            out.println(helpOps);
            mainClass = new MainClass(usr, pswd, "id_rsa");
            mainClass.configValidateWithNova(config);
            while ((line = reader.readLine()) != null)
            {
                String[] cmd = line.split("\\s+");
                if(cmd[0].equalsIgnoreCase(Commands.HELP.getCommand()) && cmd.length == 1)
                {
                    out.println("\n" + help);
                    out.println(helpOps);
                }
                else if (line.equalsIgnoreCase(Commands.EXIT.getCommand()) || line.equalsIgnoreCase(Commands.QUIT.getCommand()))
                {
                    out.println();
                    break;
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_ENV.getCommand()) && cmd.length == 1)
                {
                    out.println("===== MAIN ROUTINE =====");
                    mainClass.createEnv();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_CLUSTER.getCommand()) && cmd.length == 1)
                {
                    out.println("===== MAIN ROUTINE =====");
                    mainClass.createCluster();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_ALL.getCommand()) && cmd.length == 1)
                {
                    out.println("===== MAIN ROUTINE =====");
                    mainClass.createEnv();
                    mainClass.createCluster();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.REMOVE_CLUSTER.getCommand()) && cmd.length == 1)
                {
                    mainClass.removeCluster();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.REMOVE_ALL.getCommand()) && cmd.length == 1)
                {
                    mainClass.removeAll();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.TEST.getCommand()) && cmd.length == 1)
                {
                    mainClass.testCluster();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.IP_ADMIN_ADD.getCommand()) && cmd.length > 1)
                {
                    mainClass.addIpMasterAccess(Arrays.asList(cmd).subList(1, cmd.length), mainClass.securityGroupApi);
                }
                else if(cmd[0].equalsIgnoreCase(Commands.IP_ADMIN_REMOVE.getCommand()) && cmd.length > 1)
                {
                    mainClass.removeIpMasterAccess(Arrays.asList(cmd).subList(1, cmd.length), mainClass.securityGroupApi);
                }
                else if(cmd[0].equalsIgnoreCase(Commands.SCALE_UP.getCommand()) && cmd.length == 2)
                {
                    out.println("Not implemented in this version.");
                }
                else if(cmd[0].equalsIgnoreCase(Commands.SCALE_DOWN.getCommand()) && cmd.length == 2)
                {
                    out.println("Not implemented in this version.");
                }
                else
                {
                    out.println("Invalid command.");
                }
            }
            reader.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            exit(1);
        }
    }



    private void createEnv()
    {
        System.out.println("\nCREATING ENVIRONMENT: STARTED.\n");
        Procedures.bastionSecGroupCreate(securityGroupApi, config);
        System.out.println();
        Procedures.bastionKeyPairCreate(ssh, sshFolder, config.getBastionKeyName(), keyPairApi);
        System.out.println();
        this.bastion = Procedures.bastionServerCreate(serverApi, imageApi, flavorApi, config);
        System.out.println();
        Procedures.bastionIpAllocate(config, floatingIPApi, bastion);
        System.out.println();
        this.initBastionReference();
        System.out.println();
        Procedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        Procedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        System.out.println();
        Procedures.bastionAnsibleInstall(ssh, config, Utils.getServerPublicIp(bastion, config.getNetworkName()));
        System.out.println();
        Procedures.bastionClusterKeyCreate(ssh, config, keyPairApi, bastion, tempFolder);
        System.out.println();
        Procedures.bastionSshConfigCreate(ssh, bastion, config, tempFolder);
        System.out.println("\nCREATING ENVIRONMENT: FINISHED.\n");
    }

    private void createCluster()
    {
        System.out.println("\nCREATING CLUSTER: STARTED.\n");
        if(bastion == null && !this.initBastionReference())
        {
            exit(1);
        }
        System.out.println();
        Procedures.clusterServerGroupCreate(config, serverGroupApi);
        System.out.println();
        Procedures.createClusterVarsFile(config, tempFolder);
        System.out.println();
        Procedures.updateAmbariShellCommandFile(config);
        System.out.println();
        Procedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
        System.out.println();
        Procedures.bastionClusterProvisionExecute(ssh, config, bastion, cscAuthOnBastionCommands);
        System.out.println();
        Procedures.openAdminAccess(config, securityGroupApi);
        System.out.println();
        Procedures.generateAmbariLink(config.getClusterName(),
                Utils.getServerPublicIp(getMasterReference(config.getClusterName()), config.getNetworkName()));
        System.out.println();
        this.runBastionRoutine(false);
        System.out.println("\nCREATING CLUSTER: FINISHED.\n");
    }

    private void testCluster()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found!");
        }
        else if(getMasterReference(config.getClusterName()) == null)
        {
            System.out.println("Cluster not found!");
        }
        else
        {
            System.out.println("\nTESTING CLUSTER STARTED.\n");
            Procedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
            System.out.println();
            this.runBastionRoutine(true);
            System.out.println("\nTESTING CLUSTER FINISHED.\n");
        }
    }

    private void removeAll()
    {
        System.out.println("\nREMOVE-ALL STARTED.\n");
        this.removeCluster();
        System.out.println();
        ProceduresRemoval.removeBastion(config, serverApi, floatingIPApi, bastion);
        System.out.println();
        bastion = null;
        ProceduresRemoval.removeOsSetups(config, securityGroupApi, keyPairApi);
        System.out.println("\nREMOVE-ALL FINISHED.\n");
    }

    private void removeCluster()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found, skipping cluster deprovision.");
        }
        else if(getMasterReference(config.getClusterName()) == null)
        {
            System.out.println("Cluster not found, skipping cluster deprovision.");
        }
        else
        {
            if(!new File(tempFolder + "/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars"))).exists())
            {
                Procedures.createClusterVarsFile(config, tempFolder);
            }
            System.out.println("\nREMOVING CLUSTER STARTED.\n");
            Procedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
            System.out.println();
            ProceduresRemoval.deleteCluster(ssh, serverGroupApi, config, bastion, cscAuthOnBastionCommands);
            System.out.println("\nREMOVING CLUSTER FINISHED.\n");
        }
    }



    private boolean initBastionReference()
    {
        System.out.println("Creating Bastion java reference...");
        ImmutableList<Server> tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            if(s.getName().equals(config.getBastionMachineName()))
            {
                bastion = serverApi.get(s.getId());
                System.out.println("Bastion java reference created.");
                return true;
            }
        }
        System.out.println("Bastion host was not found!");
        return false;
    }

    private Server getMasterReference(String clusterName)
    {
        System.out.println("Creating Master java reference...");
        ImmutableList<Server> tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            if(s.getName().equals(clusterName + "-master-1"))
            {
                System.out.println("Master for " + clusterName + " found.");
                return s;
            }
        }
        System.out.println("Master host was not found.");
        return null;
    }



    private void runBastionRoutine(boolean testOnly)
    {
        System.out.println("Launching this JAR on Bastion...");
        String onlyTest;
        if(testOnly)
        {
            onlyTest = " " + testString;
        }
        else
        {
            onlyTest = "";
        }
        String commands =
                "java -jar $(pwd)/Metapipe-cPouta.jar " +
                        "username=" + this.userName + " password=" + this.password + " _bastion-routine" + onlyTest + ";";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("This JAR has finished all procedures on Bastion.");
    }

    private void bastionRoutine(boolean testOnly)
    {
        System.out.println("Launching final procedures on Bastion...");
        this.bastionRoutine_prepareMaster(testOnly);
        System.out.println();
        this.bastionRoutine_runMasterRoutine(testOnly);
        System.out.println("\nBastion routine complete.\n\n");
    }

    private void bastionRoutine_prepareMaster(boolean testOnly)
    {
        File arc = new File("arc.tar");
        String commands = "tar -xvf arc.tar --overwrite;";
        if(!testOnly)
        {
            commands += "sudo yum install -y java-1.8.0-openjdk-devel;";
        }
        Utils.sshCopier(ssh, config.getUserName(),
                Utils.getServerPrivateIp(getMasterReference(config.getClusterName()), config.getNetworkName()),
                new String[]{arc.getAbsolutePath()}, "");
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(getMasterReference(config.getClusterName()),config.getNetworkName()),
                commands);
        System.out.println("Required files prepared in Master.");
    }

    private void bastionRoutine_runMasterRoutine(boolean testOnly)
    {
        System.out.println("Launching Master routine...");
        String onlyTest;
        if(testOnly)
        {
            onlyTest = " " + testString;
        }
        else
        {
            onlyTest = "";
        }
        String commands =
                "java -jar $(pwd)/Metapipe-cPouta.jar _master-routine" + onlyTest + ";";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(getMasterReference(config.getClusterName()),config.getNetworkName()),
                commands);
    }

    private void masterRoutine(boolean testOnly)
    {
        if(!testOnly)
        {
            System.out.println("Launching final procedures on Master...");
            this.masterRoutine_clusterSetup();
            System.out.println();
        }
        this.masterRoutine_clusterTest();
        System.out.println("Master routine complete.\n\n");
    }

    private void masterRoutine_clusterSetup()
    {
        System.out.println("Launching Cluster setup on Master...");
        List<String> services = Arrays.asList("HDFS", "MAPREDUCE2", "SPARK", "ZOOKEEPER", "YARN");
        String output = "";
        boolean ready;
        String commands =
            "java -jar " + config.getXternFiles().get("ambariShellJar") +
                    " --ambari.server=localhost --ambari.port=8080 --ambari.user=admin --ambari.password=admin << EOF \n" +
            "script " + config.getXternFiles().get("ambariShellCommands") + "\nexit\n" + "EOF\n\n" +
            "sleep 5;";
        Utils.localExecutor(commands, true);
        commands =
            "java -jar " + config.getXternFiles().get("ambariShellJar") +
                    " --ambari.server=localhost --ambari.port=8080 --ambari.user=admin --ambari.password=" +
                    config.getAmbariPassword() + " << EOF \n" +
            "services start\nexit\n" + "EOF\n\n" +
            "sleep 1;";
        Utils.localExecutor(commands, true);
        while(true)
        {
            ready = true;
            for(String s : services)
            {
                try
                {
                    commands = "curl -k -u admin:" + config.getAmbariPassword() + " -H 'X-Requested-By: ambari' -X GET " +
                            "http://" + InetAddress.getLocalHost().getHostName() +".novalocal:8080/api/v1/clusters/"
                                    + config.getClusterName() + "/services/" + s;
                }
                catch (UnknownHostException e)
                {
                    e.printStackTrace();
                }
                output = Utils.localExecutor(commands, false).replaceAll(System.getProperty("line.separator"), " ");
                if(!output.contains("\"state\" : \"STARTED\""))
                {
                    System.out.println("Service " + s + " status: NOT STARTED yet.");
                    ready = false;
                }
                else
                {
                    System.out.println("Service " + s + " status: STARTED.");
                }
            }
            if(ready == true)
            {
                break;
            }
            try
            {
                System.out.println("Cluster setup not finished yet. Waiting 15 seconds...\n");
                sleep(15000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        commands = "sudo -u hdfs hadoop fs -mkdir /user/" + config.getUserName() + ";" +
                "echo 'Hadoop FS: /user/" + config.getUserName() + " created.';" +
                "sudo -u hdfs hadoop fs -chown -R " + config.getUserName() + " /user/" + config.getUserName() + ";" +
                "echo 'Hadoop FS: owner changed to " + config.getUserName() + "';" +
                "sleep 1;";
        Utils.localExecutor(commands, true);
        System.out.println("Cluster setup on Master complete.\n");
    }

    private void masterRoutine_clusterTest()
    {
        System.out.println("Launching Cluster test on Master...");
        String commands =
            "\n" + "echo 'RUNNING TEST SCRIPT ON MASTER';" + "\n" +
            "sleep 1;" +
            "/usr/hdp/current/spark-client/bin/pyspark " + config.getXternFiles().get("clusterTestScript") + ";" +
            "\n" + "echo 'RUNNING TEST SCRIPT ON ALL SLAVE NODES';" + "\n" +
            "sleep 1;" +
            "sh /usr/hdp/current/spark-client/bin/pyspark --master yarn --num-executors " +
                Integer.toString(Integer.parseInt(config.getRegularHddNodes().get("numNodes")) +
                Integer.parseInt(config.getIoHddSsdNodes().get("numNodes"))) + " " +
                config.getXternFiles().get("clusterTestScript") + ";" +
            "sleep 1;";
        Utils.localExecutor(commands, true);
        System.out.println("Cluster test complete.\n");
    }



    static void addIpMasterAccess(List<String> newIps, SecurityGroupApi securityGroupApi)
    {
        SecurityGroup masterGroup = null;
        List<String> ips;
        for(SecurityGroup sg : securityGroupApi.list())
        {
            if(sg.getName().equals(config.getClusterName() + "-master"))
            {
                masterGroup = sg;
            }
        }
        if(masterGroup == null)
        {
            System.out.println("Master security group not found!");
            return;
        }
        config.addIpAdmins(newIps);
        ips = config.getIpAdmins();
        for(String ip : ips)
        {
            try
            {
                securityGroupApi.createRuleAllowingCidrBlock(masterGroup.getId(),
                        Ingress.builder().fromPort(8080).toPort(8080).ipProtocol(IpProtocol.TCP).build(),
                        ip + "/32");
            }
            catch (IllegalStateException e)
            {
                System.out.println("Security rule for accessing Master already exists.");
            }
            System.out.println("Admin IP address added: " + ip);
        }
    }

    static void removeIpMasterAccess(List<String> ips, SecurityGroupApi securityGroupApi)
    {
        SecurityGroup masterGroup = null;
        for(SecurityGroup sg : securityGroupApi.list())
        {
            if(sg.getName().equals(config.getClusterName() + "-master"))
            {
                masterGroup = sg;
            }
        }
        if(masterGroup == null)
        {
            System.out.println("Master security group not found!");
            return;
        }
        for(String ip : ips)
        {
            for(SecurityGroupRule rule : masterGroup.getRules())
            {
                if(rule.getIpRange().contains(ip))
                {
                    securityGroupApi.deleteRule(rule.getId());
                    System.out.println("Admin IP address removed: " + ip);
                }
            }
            config.removeIpAdmins(ips);
        }
    }



    private void configValidateWithNova(Configuration config)
    {
        System.out.println("Validating 'config.yml'...");
        String errors = getValidWithNovaErrors(config);
        if(!errors.isEmpty())
        {
            //throw new IllegalStateException();
            System.out.println("\n" + errors + "\n");
            exit(1);
        }
        System.out.println("Validation finished.\n");
    }

    private String getValidWithNovaErrors(Configuration config)
    {
        String error = "";
        if(this.imageApi.list(ListImageOptions.Builder.name(config.getBastionImage())).isEmpty())
        {
            error += Configuration.errorMessagePrefix + "bastionImage\n";
        }
        if(!flavorApi.list().get(0).toString().contains("name=" + config.getBastionFlavor() + ","))
        {
            error += Configuration.errorMessagePrefix + "bastionFlavor\n";
        }
        if(!flavorApi.list().get(0).toString().contains("name=" + config.getMaster().get("flavor") + ","))
        {
            error += Configuration.errorMessagePrefix + "master flavor\n";
        }
        if(this.imageApi.list(ListImageOptions.Builder.name(config.getMaster().get("image"))).isEmpty())
        {
            error += Configuration.errorMessagePrefix + "master image\n";
        }
        if(!flavorApi.list().get(0).toString().contains("name=" + config.getRegularHddNodes().get("flavor") + ","))
        {
            error += Configuration.errorMessagePrefix + "regularHddNodes flavor\n";
        }
        if(this.imageApi.list(ListImageOptions.Builder.name(config.getRegularHddNodes().get("image"))).isEmpty())
        {
            error += Configuration.errorMessagePrefix + "regularHddNodes image\n";
        }
        if(!flavorApi.list().get(0).toString().contains("name=" + config.getIoHddSsdNodes().get("flavor") + ","))
        {
            error += Configuration.errorMessagePrefix + "getIoHddSsdNodes flavor\n";;
        }
        if(this.imageApi.list(ListImageOptions.Builder.name(config.getIoHddSsdNodes().get("image"))).isEmpty())
        {
            error += Configuration.errorMessagePrefix + "getIoHddSsdNodes image\n";
        }
        return error;
    }



    public void close() throws IOException
    {
        Closeables.close(novaApi, true);
    }

}
