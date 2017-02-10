package no.uit.metapipe.cpouta;

import com.google.common.collect.*;
import com.google.common.io.Closeables;
import com.google.inject.Module;
import com.jcraft.jsch.*;
import jline.console.ConsoleReader;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
//import org.jclouds.docker.options.ListImageOptions;
import org.jclouds.net.domain.IpProtocol;
//import org.jclouds.openstack.glance.v1_0.options.ListImageOptions;
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
import java.net.URLDecoder;
import java.util.*;

import static java.lang.System.exit;



public class MainClass implements Closeable
{

    private static Configuration config = null;

    static String testString = "test";

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
    private VolumeApi volumeApi;
    private VolumeAttachmentApi volumeAttachmentApi;
//    private ServerGroupApi serverGroupApi;

    private Server bastion;

    private JSch ssh;
    private String sshFolder = System.getProperty("user.home") + "/.ssh";

    private String osAuthOnBastionCommands;

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
        IP_ADMIN_ADD("ip-admin-add"),
        IP_ADMIN_REMOVE("ip-admin-remove"),
        LAUNCH_SW("launch"),
        STOP_SW("stop");
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

        osAuthOnBastionCommands =
                "export OS_AUTH_URL=" + config.getOsAuthName() + ";" +
                "export OS_TENANT_NAME=" + config.getProjectName() + ";" +
                "export OS_PROJECT_NAME=" + config.getProjectName() + ";" +
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
            serverApi = novaApi.getServerApi(config.getRegionName());
            flavorApi = novaApi.getFlavorApi(config.getRegionName());
            imageApi = novaApi.getImageApi(config.getRegionName());
            keyPairApi = novaApi.getKeyPairApi(config.getRegionName()).get();
            securityGroupApi = novaApi.getSecurityGroupApi(config.getRegionName()).get();
            floatingIPApi = novaApi.getFloatingIPApi(config.getRegionName()).get();
            volumeApi = novaApi.getVolumeApi(config.getRegionName()).get();
            volumeAttachmentApi = novaApi.getVolumeAttachmentApi(config.getRegionName()).get();
//            serverGroupApi = novaApi.getServerGroupApi(config.getRegionName()).get();
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
                "username=OS_Username " +
                "password=OS_Password \n";
        final String help =
                "BE SURE THAT CONFIG.YML IS CONFIGURED CORRECTLY.\n";
        final String helpOps =
                "Commands:\n" +
                "help: This message.\n" +
                "quit/exit: Exit.\n" +
                "create-env: Create environment - required OS settings, bastion host \n" +
                "create-cluster: Create a new cluster and set up pipe software " +
                    "(given that 'create-env' was executed before, and cluster doesn't exist).\n" +
                "create-all: 'create-env' + 'create-cluster'\n" +
                "test: Run test script on the existing cluster, run validation of installed pipe software.\n" +
                "launch: Launch the installed pipe software.\n" +
                "stop: Kills all spark processes that are currently running on the cluster.\n" +
                "remove-cluster: Remove existing cluster and keep the OS environment for future use.\n" +
                "remove-all: Remove existing cluster, bastion, OS setups, cleanup everything.\n" +
                "ip-admin-add X.X.X.X: Adding IP to admins will give the IP-owner access to cluster management web-gui.\n" +
                "ip-admin-remove X.X.X.X: Remove the IP from admins.\n";
        String usr = null, pswd = null;

        config = Configuration.loadConfig();
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
                if(arg.trim().contains("_bastion-routine="))
                {
                    System.out.println("===== BASTION ROUTINE =====");
                    mainClass = new MainClass(usr, pswd, config.getClusterKeyFileName());
                    mainClass.initBastionReference();
                    BastionRoutine.bastionRoutine(Integer.parseInt(arg.trim().replace("_bastion-routine=", "")),
                            mainClass.ssh, config, mainClass.bastion, mainClass.getMasterReference(config.getClusterName()),
                            mainClass.volumeApi, mainClass.volumeAttachmentApi);
                    return;
                }
                else if(arg.trim().contains("_master-routine="))
                {
                    System.out.println("===== MASTER ROUTINE =====");
                    mainClass = new MainClass(usr, pswd, null);
                    MasterRoutine.masterRoutine(config, arg.contains(testString));
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
                config = Configuration.loadConfig();
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
                    mainClass.createEnv();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_CLUSTER.getCommand()) && cmd.length == 1)
                {
                    mainClass.createCluster();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_ALL.getCommand()) && cmd.length == 1)
                {
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
                    mainClass.test();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.IP_ADMIN_ADD.getCommand()) && cmd.length > 1)
                {
                    mainClass.addIpMasterAccess(Arrays.asList(cmd).subList(1, cmd.length), mainClass.securityGroupApi);
                }
                else if(cmd[0].equalsIgnoreCase(Commands.IP_ADMIN_REMOVE.getCommand()) && cmd.length > 1)
                {
                    mainClass.removeIpMasterAccess(Arrays.asList(cmd).subList(1, cmd.length), mainClass.securityGroupApi);
                }
                else if(cmd[0].equalsIgnoreCase(Commands.LAUNCH_SW.getCommand()) && cmd.length == 1)
                {
                    mainClass.launchSW();
                }
                else if(cmd[0].equalsIgnoreCase(Commands.STOP_SW.getCommand()) && cmd.length == 1)
                {
                    mainClass.stopSW();
                }
                else
                {
                    devArgs(cmd, out, mainClass);
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
        config = Configuration.loadConfig();
        System.out.println("\nCREATING ENVIRONMENT: STARTED.\n");
        ClientProcedures.bastionSecGroupCreate(securityGroupApi, config);
        System.out.println();
        ClientProcedures.bastionKeyPairCreate(ssh, sshFolder, config.getBastionKeyName(), keyPairApi);
        System.out.println();
        this.bastion = ClientProcedures.bastionServerCreate(serverApi, imageApi, flavorApi, config);
        System.out.println();
        ClientProcedures.bastionIpAllocate(config, floatingIPApi, bastion);
        System.out.println();
        this.initBastionReference();
        System.out.println();
        ClientProcedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        ClientProcedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        System.out.println();
        ClientProcedures.bastionAnsibleInstall(ssh, config, Utils.getServerPublicIp(bastion, config.getNetworkName()));
        System.out.println();
        ClientProcedures.bastionClusterKeyCreate(ssh, config, keyPairApi, bastion, tempFolder);
        System.out.println();
        ClientProcedures.bastionSshConfigCreate(ssh, bastion, config, tempFolder);
        System.out.println();
        ClientProcedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
        System.out.println();
        ClientProcedures.createDisk4SW(ssh, config, volumeApi, bastion, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.attachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.transferInstallationFiles2VDisk(ssh, config, bastion, false);
        System.out.println();
        ClientProcedures.detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println("\nCREATING ENVIRONMENT: FINISHED.\n");
    }

    private void createCluster()
    {
        config = Configuration.loadConfig();
        System.out.println("\nCREATING CLUSTER: STARTED.\n");
        if(bastion == null && !this.initBastionReference())
        {
            exit(1);
        }
        System.out.println();
//        ClientProcedures.clusterServerGroupCreate(config, userName, password);
//        System.out.println();
        ClientProcedures.createClusterVarsFile(config, tempFolder);
        System.out.println();
//        ClientProcedures.updateAmbariShellCommandFile(config);
//        System.out.println();
        ClientProcedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
        System.out.println();
        ClientProcedures.updateInstallationBashScripts(ssh, config, bastion, bastion, true,
                volumeApi, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.bastionClusterProvisionExecute(ssh, config, bastion, osAuthOnBastionCommands);
        System.out.println();
        ClientProcedures.bastionClusterConfigurationExecute(ssh, config, bastion, osAuthOnBastionCommands);
        System.out.println();
        ClientProcedures.openAdminAccess(config, securityGroupApi);
        System.out.println();
        ClientProcedures.generateWebGuiLink(config.getClusterName(),
                Utils.getServerPublicIp(getMasterReference(config.getClusterName()), config.getNetworkName()));
        System.out.println();
//        ClientProcedures.attachSwDisk(config, getMasterReference(config.getClusterName()), volumeApi, volumeAttachmentApi);
        this.runBastionRoutine(0);
//        ClientProcedures.detachSwDisk(config, getMasterReference(config.getClusterName()), volumeApi, volumeAttachmentApi);
        System.out.println("\nCREATING CLUSTER: FINISHED.\n");
    }

    private void test()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found!\n");
        }
        else if(getMasterReference(config.getClusterName()) == null)
        {
            System.out.println("Cluster not found!\n");
        }
        else
        {
            System.out.println("\nTESTING CLUSTER STARTED.\n");
            ClientProcedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
            ClientProcedures.updateInstallationBashScripts(ssh, config, bastion, bastion, true,
                    volumeApi, volumeAttachmentApi);
            System.out.println();
            this.runBastionRoutine(1);
            System.out.println("\nTESTING CLUSTER FINISHED.\n");
        }
    }

    private void launchSW()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found!\n");
        }
        else if(getMasterReference(config.getClusterName()) == null)
        {
            System.out.println("Cluster not found!\n");
        }
        else
        {
            System.out.println("\nLAUNCHING INSTALLED SOFTWARE.\n");
            ClientProcedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
            ClientProcedures.updateInstallationBashScripts(ssh, config, bastion, bastion, true,
                    volumeApi, volumeAttachmentApi);
            System.out.println();
            this.runBastionRoutine(2);
        }
    }

    private void stopSW()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found!\n");
        }
        else if(getMasterReference(config.getClusterName()) == null)
        {
            System.out.println("Cluster not found!\n");
        }
        else
        {
            System.out.println("\nLAUNCHING INSTALLED SOFTWARE.\n");
            ClientProcedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
            ClientProcedures.updateInstallationBashScripts(ssh, config, bastion, bastion, true,
                    volumeApi, volumeAttachmentApi);
            System.out.println();
            this.runBastionRoutine(3);
        }
    }

    private void removeAll()
    {
        System.out.println("\nREMOVE-ALL STARTED.\n");
        this.removeCluster();
        System.out.println();
        ClientProceduresRemoval.removeDisk4SW(ssh, config, volumeApi, volumeAttachmentApi,  bastion);
        System.out.println();
        ClientProceduresRemoval.removeBastion(config, serverApi, floatingIPApi, bastion);
        System.out.println();
        bastion = null;
        ClientProceduresRemoval.removeOsSetups(config, securityGroupApi, keyPairApi);
        System.out.println("\nREMOVE-ALL FINISHED.\n");
    }

    private void removeCluster()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found, skipping cluster deprovision.");
        }
//        else if(getMasterReference(config.getClusterName()) == null)
//        {
//            System.out.println("Cluster not found, skipping cluster deprovision.");
//        }
        else
        {
            if(!new File(tempFolder + "/" + Utils.getFileNameFromPath(config.getXternFiles().get("ansibleClusterVars"))).exists())
            {
                ClientProcedures.createClusterVarsFile(config, tempFolder);
            }
            System.out.println("\nREMOVING CLUSTER STARTED.\n");
            ClientProcedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
            System.out.println();
            ClientProceduresRemoval.deleteCluster(ssh, /*serverGroupApi, */config, bastion,
                    getMasterReference(config.getClusterName()), osAuthOnBastionCommands, volumeApi, volumeAttachmentApi);
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



    private void runBastionRoutine(int mode)
    {
        System.out.println("Launching this JAR on Bastion...");
        String commands =
                "java -jar Metapipe-cPouta.jar " +
                        "username=" + this.userName + " password=" + this.password + " _bastion-routine=" + mode + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("This JAR has finished all procedures on Bastion.");
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
                securityGroupApi.createRuleAllowingCidrBlock(masterGroup.getId(),
                        Ingress.builder().fromPort(4040).toPort(4050).ipProtocol(IpProtocol.TCP).build(),
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
        String images = imageApi.list().get(0).toString();
        String flavors = flavorApi.list().get(0).toString();
        if(!images.contains("name=" + config.getImageDefault() + ","))
        {
            error += Configuration.errorMessagePrefix + "bastionImage\n";
        }
        if(!flavors.contains("name=" + config.getBastionFlavor() + ","))
        {
            error += Configuration.errorMessagePrefix + "bastionFlavor\n";
        }
        if(!flavors.contains("name=" + config.getMaster().get("flavor") + ","))
        {
            error += Configuration.errorMessagePrefix + "master flavor\n";
        }
        if(!flavors.contains("name=" + config.getRegularHddNodes().get("flavor") + ","))
        {
            error += Configuration.errorMessagePrefix + "regularHddNodes flavor\n";
        }
        if(!flavors.contains("name=" + config.getIoHddSsdNodes().get("flavor") + ","))
        {
            error += Configuration.errorMessagePrefix + "getIoHddSsdNodes flavor\n";;
        }
        return error;
    }



    static void devArgs(String[] cmd, PrintWriter out, MainClass mainClass)
    {
        // To test each procedure, not to be used by user
        if(cmd[0].contains("-ce.") && cmd.length == 1)
        {
            config = Configuration.loadConfig();
            if(cmd[0].equals("-ce.a"))
                ClientProcedures.bastionSecGroupCreate(mainClass.securityGroupApi, config);
            else if(cmd[0].equals("-ce.b"))
                ClientProcedures.bastionKeyPairCreate(mainClass.ssh, mainClass.sshFolder,
                        config.getBastionKeyName(), mainClass.keyPairApi);
            else if(cmd[0].equals("-ce.c"))
                ClientProcedures.bastionServerCreate(mainClass.serverApi,
                        mainClass.imageApi, mainClass.flavorApi, config);
            mainClass.initBastionReference();
            if(cmd[0].equals("-ce.d"))
                ClientProcedures.bastionIpAllocate(config, mainClass.floatingIPApi, mainClass.bastion);
            else if(cmd[0].equals("-ce.e"))
                ClientProcedures.bastionPackagesInstall(mainClass.ssh, mainClass.bastion, config, mainClass.serverApi);
            else if(cmd[0].equals("-ce.f"))
                ClientProcedures.bastionAnsibleInstall(mainClass.ssh, config,
                        Utils.getServerPublicIp(mainClass.bastion, config.getNetworkName()));
            else if(cmd[0].equals("-ce.g"))
                ClientProcedures.bastionClusterKeyCreate(mainClass.ssh, config, mainClass.keyPairApi,
                        mainClass.bastion, mainClass.tempFolder);
            else if(cmd[0].equals("-ce.h"))
                ClientProcedures.bastionSshConfigCreate(mainClass.ssh, mainClass.bastion, config, mainClass.tempFolder);
            else if(cmd[0].equals("-ce.i"))
                ClientProcedures.createDisk4SW(mainClass.ssh, config, mainClass.volumeApi,
                        mainClass.bastion, mainClass.volumeAttachmentApi);
            else if(cmd[0].equals("-ce.j"))
                ClientProcedures.attachSwDisk(config, mainClass.bastion, mainClass.volumeApi,
                        mainClass.volumeAttachmentApi);
            else if(cmd[0].equals("-ce.k"))
                ClientProcedures.transferInstallationFiles2VDisk(mainClass.ssh, config, mainClass.bastion, false);
            else if(cmd[0].equals("-ce.l"))
                ClientProcedures.detachSwDisk(config, mainClass.bastion, mainClass.volumeApi,
                        mainClass.volumeAttachmentApi);
        }
        else if(cmd[0].contains("-cc.") && cmd.length == 1)
        {
            config = Configuration.loadConfig();
            mainClass.initBastionReference();
            ClientProcedures.transferRequiredFiles2Bastion(mainClass.ssh, config, mainClass.bastion, mainClass.tempFolder);
            /*if(cmd[0].equals("-cc.a"))
                ClientProcedures.clusterServerGroupCreate(config, mainClass.userName, mainClass.password);
            else*/ if(cmd[0].equals("-cc.b"))
                ClientProcedures.createClusterVarsFile(config, mainClass.tempFolder);
//            else if(cmd[0].equals("-cc.c"))
//                ClientProcedures.updateAmbariShellCommandFile(config);
//            else if(cmd[0].equals("-cc.d"))
//                ClientProcedures.transferRequiredFiles2Bastion(mainClass.ssh, config, mainClass.bastion, mainClass.tempFolder);
            else if(cmd[0].equals("-cc.e"))
                ClientProcedures.bastionClusterProvisionExecute(mainClass.ssh, config,
                        mainClass.bastion, mainClass.osAuthOnBastionCommands);
            else if(cmd[0].equals("-cc.f"))
                ClientProcedures.bastionClusterConfigurationExecute(mainClass.ssh, config,
                        mainClass.bastion, mainClass.osAuthOnBastionCommands);
            else if(cmd[0].equals("-cc.g"))
                ClientProcedures.openAdminAccess(config, mainClass.securityGroupApi);
            else if(cmd[0].equals("-cc.h"))
                ClientProcedures.generateWebGuiLink(config.getClusterName(),
                        Utils.getServerPublicIp(mainClass.getMasterReference(config.getClusterName()), config.getNetworkName()));
            else if(cmd[0].equals("-cc.i"))
            {
                ClientProcedures.updateInstallationBashScripts(mainClass.ssh, config, mainClass.bastion,
                        mainClass.bastion, true, mainClass.volumeApi, mainClass.volumeAttachmentApi);
                mainClass.runBastionRoutine(0);
            }
        }
        else if(cmd[0].contains("-r.") && cmd.length == 1)
        {
            mainClass.initBastionReference();
            if(cmd[0].equals("-r.a"))
                ClientProceduresRemoval.deleteCluster(mainClass.ssh, /*serverGroupApi, */config,
                        mainClass.bastion, mainClass.getMasterReference(config.getClusterName()),
                        mainClass.osAuthOnBastionCommands, mainClass.volumeApi, mainClass.volumeAttachmentApi);
            else if(cmd[0].equals("-r.b"))
                ClientProceduresRemoval.removeDisk4SW(mainClass.ssh, config, mainClass.volumeApi,
                        mainClass.volumeAttachmentApi, mainClass.bastion);
            else if(cmd[0].equals("-r.c"))
                ClientProceduresRemoval.removeBastion(config, mainClass.serverApi, mainClass.floatingIPApi,
                        mainClass.bastion);
            else if(cmd[0].equals("-r.d"))
                ClientProceduresRemoval.removeOsSetups(config, mainClass.securityGroupApi, mainClass.keyPairApi);
        }
        else
        {
            out.println("Invalid command.");
        }
    }



    public void close() throws IOException
    {
        Closeables.close(novaApi, true);
    }

}
