package no.uit.metapipe.cpouta;

import com.google.common.collect.*;
import com.google.common.io.Closeables;
import com.google.inject.Module;
import com.jcraft.jsch.*;
import jline.console.ConsoleReader;
import jline.console.completer.StringsCompleter;
import org.apache.commons.io.output.TeeOutputStream;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.net.domain.IpProtocol;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.CinderApiMetadata;
import org.jclouds.openstack.cinder.v1.features.SnapshotApi;
import org.jclouds.openstack.keystone.v2_0.KeystoneApi;
import org.jclouds.openstack.keystone.v2_0.KeystoneApiMetadata;
import org.jclouds.openstack.keystone.v2_0.config.CredentialTypes;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.keystone.v2_0.domain.Tenant;
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
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.System.exit;



public class MainClass implements Closeable
{

    private static Configuration config = null;

    private String userName;
    private String password;

    private File tempFolderFile;

    private PrintStream printStream;
    private TeeOutputStream printStreamTee;

    private File logsFolderFile;
    private FileOutputStream logsStream;
    private String logsFileName;

    private NovaApi novaApi;
    private KeystoneApi keystoneApi;
    private CinderApi cinderApi;
    private Tenant tenant;
    private Set<String> regions;
    private SecurityGroupApi securityGroupApi;
    private ServerApi serverApi;
    private FlavorApi flavorApi;
    private ImageApi imageApi;
    private KeyPairApi keyPairApi;
    private FloatingIPApi floatingIPApi;
    private VolumeApi volumeApi;
    private VolumeAttachmentApi volumeAttachmentApi;
    private SnapshotApi volumeSnapshotApi;
//    private ServerGroupApi serverGroupApi;

    private HardwareStats hardwareStats;

    private Server bastion;

    private JSch ssh;
    private String sshFolder = System.getProperty("user.home") + "/.ssh";

    private String osAuthOnBastionCommands;

    private Stopwatch stopwatch;

    enum Commands
    {
        HELP("help"),
        QUIT("quit"),
        EXIT("exit"),
        STAT("overview"),
        CREATE_ENV("create-env"),
        CREATE_CLUSTER("create-cluster"),
        CREATE_ALL("create-all"),
        TEST("test"),
        TEST_DEV("test-dev"),
        SW_LAUNCH("sw-launch"),
        SW_LAUNCH_DEV("sw-launch-dev"),
        SW_STOP("sw-kill"),
        SW_STOP_DEV("sw-kill-dev"),
        SW_UPDATE("sw-update"),
        REMOVE_CLUSTER("remove-cluster"),
        REMOVE_ENV("remove-env"),
        REMOVE_ALL("remove-all"),
        REMOVE_CREATE_CLUSTER("remove-create-cluster"),
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

    private MainClass(String userName, String password, String idRsaKeyFileName, boolean isBastionRoutine)
    {
        Utils.printTimestampedMessage(System.out, "\n", "Starting...", "\n\n");

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

            keystoneApi = ContextBuilder.newBuilder(new KeystoneApiMetadata())
                    .endpoint(config.getOsAuthName())
                    .credentials(userName, password)
                    .modules(modules)
                    .buildApi(KeystoneApi.class);

            tenant = null;
            for(Tenant x : keystoneApi.getTenantApi().get().list().get(0))
            {
                if(x.getName().equals(config.getProjectName()))
                {
                    tenant = x;
                    break;
                }
            }
            if(tenant == null)
            {
                System.out.println("\nUnexpected error: Tenant object from KeystoneApi/TenantApi not found. \n");
                exit(1);
            }

            cinderApi = ContextBuilder.newBuilder(new CinderApiMetadata())
                    .endpoint(config.getOsAuthName())
                    .credentials(tenant.getName() + ":" + userName, password)
                    .modules(modules)
                    .buildApi(CinderApi.class);

            regions = novaApi.getConfiguredRegions();
            serverApi = novaApi.getServerApi(config.getRegionName());
            flavorApi = novaApi.getFlavorApi(config.getRegionName());
            imageApi = novaApi.getImageApi(config.getRegionName());
            keyPairApi = novaApi.getKeyPairApi(config.getRegionName()).get();
            securityGroupApi = novaApi.getSecurityGroupApi(config.getRegionName()).get();
            floatingIPApi = novaApi.getFloatingIPApi(config.getRegionName()).get();
            volumeApi = novaApi.getVolumeApi(config.getRegionName()).get();
            volumeAttachmentApi = novaApi.getVolumeAttachmentApi(config.getRegionName()).get();
            volumeSnapshotApi = cinderApi.getSnapshotApi(config.getRegionName());
//            serverGroupApi = novaApi.getServerGroupApi(config.getRegionName()).get();
            hardwareStats = HardwareStats.loadHardwareStats(config, novaApi, tenant, cinderApi);
        }

        String path = MainClass.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = "";
        try {
            decodedPath = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            exit(1);
        }

        tempFolderFile = new File("temp");
        if(!tempFolderFile.exists())
        {
            tempFolderFile.mkdir();
        }
        System.out.print("\nTemporary folder: " + tempFolderFile.getAbsolutePath() + "\n");

        if(!isBastionRoutine)
        {
            logsFolderFile = new File ("logs");
            if(!logsFolderFile.exists())
            {
                logsFolderFile.mkdir();
            }
            logsFileName = "log_" + (new SimpleDateFormat("yyyyMMdd-HHmmss")).format(Calendar.getInstance().getTime()) + ".txt";
            try
            {
                logsStream = new FileOutputStream(new File(logsFolderFile.getAbsolutePath() + "/" + logsFileName));
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            printStreamTee = new TeeOutputStream(System.out, logsStream);
            printStream = new PrintStream(printStreamTee);
            System.setOut(printStream);
            System.out.print("\nLogs folder: " + logsFolderFile.getAbsolutePath() + "\n\n");
        }

        stopwatch = new Stopwatch();
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
                "COMMANDS-LIST:\n" +
                Commands.HELP.getCommand() + ": This message.\n" +
                Commands.QUIT.getCommand() + "/" + Commands.EXIT.getCommand() + ": Exit.\n" +
                Commands.STAT.getCommand() + ": Info about total/used/available resources, resources required for 'create-*' commands.\n" +
                Commands.CREATE_ENV.getCommand() + ": Create environment - required OS settings, bastion host \n" +
                Commands.CREATE_CLUSTER.getCommand() + ": Create a new cluster and set up Pipe software " +
                        "(given that 'create-env' was executed before, and cluster doesn't exist).\n" +
                Commands.CREATE_ALL.getCommand() + ": 'create-env' + 'create-cluster'\n" +
                Commands.TEST.getCommand() + ": Run test script on the existing Spark cluster, run validation of installed Pipe software.\n" +
                Commands.TEST_DEV.getCommand() + ": Run test script on the existing Spark cluster, run validation of installed Pipe software. Before testing, the components of the tool are updated on Bastion and Master.\n" +
                Commands.SW_LAUNCH.getCommand() + ": Launch the installed Pipe software.\n" +
                Commands.SW_LAUNCH_DEV.getCommand() + ": Launch the installed Pipe software. Before launching, the components of the tool are updated on Bastion and Master.\n" +
                Commands.SW_STOP.getCommand() + ": Stops all sw/spark processes running on the cluster.\n" +
                Commands.SW_STOP_DEV.getCommand() + ": Stops all sw/spark processes running on the cluster. Before launching, the components of the tool are updated on Bastion and Master.\n" +
                Commands.SW_UPDATE.getCommand() + ": Re-download Pipe software to the existing environment, and re-install it on the cluster if it exists.\n" +
                Commands.REMOVE_CLUSTER.getCommand() + ": Remove existing cluster and keep the OS environment for future use.\n" +
                Commands.REMOVE_ENV.getCommand() + ": Remove bastion, OS setups, SW-disk; config.yml cleanup. Cluster VMs removal skipped. \n" +
                Commands.REMOVE_ALL.getCommand() + ": Remove cluster VMs, bastion, OS setups, SW-disk, cleanup everything.\n" +
                Commands.REMOVE_CREATE_CLUSTER.getCommand() + ": Runs '" + Commands.REMOVE_CLUSTER.getCommand() +
                        "' and then '" + Commands.CREATE_CLUSTER.getCommand() + "'.\n" +
                Commands.IP_ADMIN_ADD.getCommand() + " X.X.X.X: Adding IP to admins will give the IP-owner access to cluster management web-gui.\n" +
                Commands.IP_ADMIN_REMOVE.getCommand() + " X.X.X.X: Remove the IP from admins.\n";
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
                    mainClass = new MainClass(usr, pswd, config.getClusterKeyFileName(), true);
                    mainClass.initBastionReference();
                    BastionRoutine.bastionRoutine(arg.trim().replace("_bastion-routine=", ""),
                            mainClass.ssh, config, mainClass.getMasterReference(config.getClusterName()));
                    return;
                }
//                else if(arg.trim().contains("_master-routine="))
//                {
//                    System.out.println("===== MASTER ROUTINE =====");
//                    mainClass = new MainClass(usr, pswd, null);
//                    MasterRoutine.masterRoutine(config, arg.contains(stringTest));
//                    return;
//                }
            }
        }
        InputStream inStream = new FileInputStream(FileDescriptor.in);
        ConsoleReader reader;
        try
        {
            reader = new ConsoleReader(programName, inStream, System.out, null);
            reader.setPrompt("> ");
            //reader.addCompleter(new FileNameCompleter()); // This FileNameCompleter disables the following StringsCompleter for the commands for some reason
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

            mainClass = new MainClass(usr, pswd, "id_rsa", false);
            mainClass.configValidateWithNova(config);
            ClientProcedures.prepareToolComponents(config, mainClass.tempFolderFile.getAbsolutePath(), mainClass.flavorApi, false);
            out.println("\n\n" + helpOps + "\n");
            out.println(help);
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
                else if(cmd[0].equalsIgnoreCase(Commands.STAT.getCommand()) && cmd.length == 1)
                {
                    mainClass.hardwareStats = HardwareStats.loadHardwareStats(config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi);
                    mainClass.hardwareStats.printStats(System.out, config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi);
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_ENV.getCommand()) && cmd.length == 1)
                {
                    mainClass.hardwareStats = HardwareStats.loadHardwareStats(config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi);
                    if(mainClass.hardwareStats.canCreateEnv(System.out, config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi))
                    {
                        mainClass.stopwatch.start();
                        mainClass.createEnv();
                        mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                    }
                    else
                    {
                        mainClass.printStream.println("OPERATION CANCELED.\n");
                    }
                }
                else if((cmd[0].equalsIgnoreCase(Commands.CREATE_CLUSTER.getCommand()) ||
                        cmd[0].equalsIgnoreCase(Commands.REMOVE_CREATE_CLUSTER.getCommand())) &&
                        cmd.length == 1)
                {
                    mainClass.stopwatch.start();
                    if(cmd[0].equalsIgnoreCase(Commands.REMOVE_CREATE_CLUSTER.getCommand()))
                    {
                        mainClass.removeCluster();
                    }
                    mainClass.hardwareStats = HardwareStats.loadHardwareStats(config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi);
                    if(mainClass.hardwareStats.canCreateCluster(System.out, config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi))
                    {
                        mainClass.createCluster(out);
                    }
                    else
                    {
                        mainClass.printStream.println("OPERATION CANCELED.\n");
                    }
                    mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                }
                else if(cmd[0].equalsIgnoreCase(Commands.CREATE_ALL.getCommand()) && cmd.length == 1)
                {
                    mainClass.hardwareStats = HardwareStats.loadHardwareStats(config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi);
                    if(mainClass.hardwareStats.canCreateAll(System.out, config, mainClass.novaApi, mainClass.tenant, mainClass.cinderApi))
                    {
                        mainClass.stopwatch.start();
                        mainClass.createEnv();
                        mainClass.createCluster(out);
                        mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                    }
                    else
                    {
                        mainClass.printStream.println("OPERATION CANCELED.\n");
                    }
                }
                else if(cmd[0].equalsIgnoreCase(Commands.REMOVE_CLUSTER.getCommand()) && cmd.length == 1)
                {
                    mainClass.stopwatch.start();
                    mainClass.removeCluster();
                    mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                }
                else if(cmd[0].equalsIgnoreCase(Commands.REMOVE_ALL.getCommand()) && cmd.length == 1)
                {
                    mainClass.stopwatch.start();
                    mainClass.removeAll();
                    mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                }
                else if(cmd[0].equalsIgnoreCase(Commands.REMOVE_ENV.getCommand()) && cmd.length == 1)
                {
                    mainClass.stopwatch.start();
                    mainClass.removeEnv();
                    mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                }
                else if((cmd[0].equalsIgnoreCase(Commands.TEST.getCommand()) || cmd[0].equalsIgnoreCase(Commands.TEST_DEV.getCommand())) && cmd.length == 1)
                {
                    mainClass.stopwatch.start();
                    ClientProcedures.prepareToolComponents(config, mainClass.tempFolderFile.getAbsolutePath(), mainClass.flavorApi, false);
                    mainClass.test(cmd[0].equalsIgnoreCase(Commands.TEST_DEV.getCommand()));
                    mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
                }
                else if(cmd[0].equalsIgnoreCase(Commands.IP_ADMIN_ADD.getCommand()) && cmd.length > 1)
                {
                    mainClass.addIpMasterAccess(Arrays.asList(cmd).subList(1, cmd.length), mainClass.securityGroupApi);
                }
                else if(cmd[0].equalsIgnoreCase(Commands.IP_ADMIN_REMOVE.getCommand()) && cmd.length > 1)
                {
                    mainClass.removeIpMasterAccess(Arrays.asList(cmd).subList(1, cmd.length), mainClass.securityGroupApi);
                }
                else if((cmd[0].equalsIgnoreCase(Commands.SW_LAUNCH.getCommand()) || cmd[0].equalsIgnoreCase(Commands.SW_LAUNCH_DEV.getCommand())) &&
                        cmd.length == 1)
                {
                    ClientProcedures.prepareToolComponents(config, mainClass.tempFolderFile.getAbsolutePath(), mainClass.flavorApi, false);
                    mainClass.launchSW(cmd[0].equalsIgnoreCase(Commands.SW_LAUNCH_DEV.getCommand()));
                }
                else if((cmd[0].equalsIgnoreCase(Commands.SW_STOP.getCommand()) || cmd[0].equalsIgnoreCase(Commands.SW_STOP_DEV.getCommand())) &&
                        cmd.length == 1)
                {
                    ClientProcedures.prepareToolComponents(config, mainClass.tempFolderFile.getAbsolutePath(), mainClass.flavorApi, false);
                    mainClass.stopSW(cmd[0].equalsIgnoreCase(Commands.SW_STOP_DEV.getCommand()));
                }
                else if(cmd[0].equalsIgnoreCase(Commands.SW_UPDATE.getCommand()) && cmd.length == 1)
                {
                    mainClass.stopwatch.start();
                    ClientProcedures.prepareToolComponents(config, mainClass.tempFolderFile.getAbsolutePath(), mainClass.flavorApi, false);
                    mainClass.updateSW();
                    mainClass.printStream.println("Execution time: " + mainClass.stopwatch.stopGetResultReset() + "\n");
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
        ClientProcedures.prepareToolComponents(config, tempFolderFile.getAbsolutePath(), flavorApi, true);

        Utils.printTimestampedMessage(System.out, "\n", "CREATING ENVIRONMENT: STARTED.", "\n");
        ClientProcedures.bastionSecGroupCreate(securityGroupApi, config);
        System.out.println();
        ClientProcedures.bastionKeyPairCreate(ssh, sshFolder, config.getBastionKeyName(), keyPairApi);
        System.out.println();
        this.bastion = ClientProcedures.bastionServerCreate(serverApi, imageApi, flavorApi, config, ssh);
        System.out.println();
        ClientProcedures.bastionIpAllocate(config, floatingIPApi, bastion);
        System.out.println();
        this.initBastionReference();
        System.out.println();
        ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
        System.out.println();
        ClientProcedures.disablePasswordAuth(ssh, config, this.bastion);
        System.out.println();
        ClientProcedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        ClientProcedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        System.out.println();
        ClientProcedures.bastionAnsibleInstall(ssh, config, Utils.getServerPublicIp(bastion, config.getNetworkName()));
        System.out.println();
        ClientProcedures.bastionClusterKeyCreate(ssh, config, keyPairApi, bastion, tempFolderFile.getAbsolutePath());
        System.out.println();
        ClientProcedures.bastionSshConfigCreate(ssh, bastion, config, tempFolderFile.getAbsolutePath());
        System.out.println();
        ClientProcedures.createSwDisk(ssh, config, volumeApi, bastion, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.attachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.mountSwDisk(ssh, config, bastion, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.runSwDiskPreparation(ssh, config, bastion, false);
        System.out.println();
        ClientProcedures.unmountSwDisk(ssh, config, bastion);
        System.out.println();
        ClientProcedures.detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.prepareToolComponents(config, tempFolderFile.getAbsolutePath(), flavorApi, true);
        System.out.println();
        Utils.printTimestampedMessage(System.out, "\n", "CREATING ENVIRONMENT: FINISHED.", "\n");
    }

    private void createCluster(PrintWriter out)
    {
        ClientProcedures.prepareToolComponents(config, tempFolderFile.getAbsolutePath(), flavorApi, true);

        Utils.printTimestampedMessage(System.out, "\n", "CREATING CLUSTER: STARTED.", "\n");
        if(bastion == null && !this.initBastionReference())
        {
            System.out.println("SOMETHING WENT WEIRD, BASTION NOT FOUND!\n");
            return;
        }
        if(!ClientProcedures.swDiskExists(config, volumeApi) || !Utils.objectHasContents(config.getSwDiskID()))
        {
            System.out.println("SOMETHING WENT WEIRD, SW-DISK NOT FOUND!\n");
            return;
        }
        System.out.println();
//        ClientProcedures.clusterServerGroupCreate(config, userName, password);
//        System.out.println();
        ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
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
        this.runBastionRoutine(Commands.CREATE_CLUSTER.getCommand());
        Utils.printTimestampedMessage(System.out, "\n", "CREATING CLUSTER: FINISHED.", "\n");
    }

    private void test(boolean devScriptsUpdate)
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
            Utils.printTimestampedMessage(System.out, "\n", "TESTING CLUSTER: STARTED.", "\n");
            if(devScriptsUpdate)
            {
                ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
                System.out.println();
                this.runBastionRoutine(Commands.TEST_DEV.getCommand());
            }
            else
            {
                this.runBastionRoutine(Commands.TEST.getCommand());
            }
            Utils.printTimestampedMessage(System.out, "\n", "TESTING CLUSTER: FINISHED.", "\n");
        }
    }

    private void launchSW(boolean devScriptsUpdate)
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
            Utils.printTimestampedMessage(System.out, "\n", "LAUNCHING PIPE SOFTWARE...", "\n");
            if(devScriptsUpdate)
            {
                ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
                System.out.println();
                this.runBastionRoutine(Commands.SW_LAUNCH_DEV.getCommand());
            }
            else
            {
                this.runBastionRoutine(Commands.SW_LAUNCH.getCommand());
            }
        }
    }

    private void stopSW(boolean devScriptsUpdate)
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
            Utils.printTimestampedMessage(System.out, "\n", "STOPPING PIPE SOFTWARE...", "\n");
            if(devScriptsUpdate)
            {
                ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
                System.out.println();
                this.runBastionRoutine(Commands.SW_STOP_DEV.getCommand());
            }
            else
            {
                this.runBastionRoutine(Commands.SW_STOP.getCommand());
            }
        }
    }

    private void updateSW()
    {
        Utils.printTimestampedMessage(System.out, "\n", "SW-UPDATE: STARTED.", "\n");
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found! SW-UPDATE is meant to be run after CREATE-ENV or CREATE-ALL.\n");
            return;
        }
        if(!ClientProcedures.swDiskExists(config, volumeApi))
        {
            System.out.println("SW-disk not found! SW-UPDATE is meant to be run after CREATE-ENV or CREATE-ALL.\n");
            return;
        }
        System.out.println();
        ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
        System.out.println();
        ClientProcedures.attachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        System.out.println();
        ClientProcedures.runSwDiskPreparation(ssh, config, bastion, true);
        System.out.println();
        ClientProcedures.detachSwDisk(config, bastion, volumeApi, volumeAttachmentApi);
        Utils.printTimestampedMessage(System.out, "\n", "SW-UPDATE: FINISHED.", "\n");
        if(getMasterReference(config.getClusterName()) != null)
        {
            System.out.println("To start using the updated SW, remove the existing cluster and create a new cluster.\n");
        }
    }

    private void removeAll()
    {
        Utils.printTimestampedMessage(System.out, "\n", "REMOVE-ALL: STARTED.", "\n");
        this.removeCluster();
        this.removeEnv();
        Utils.printTimestampedMessage(System.out, "\n", "REMOVE-ALL: FINISHED.", "\n");
    }

    private void removeEnv()
    {
        this.initBastionReference();
        Utils.printTimestampedMessage(System.out, "\n", "REMOVE-ENV: STARTED.", "\n");
        ClientProceduresRemoval.removeSwCaches(ssh, config, volumeApi, volumeAttachmentApi,  bastion, true);
        System.out.println();
        ClientProceduresRemoval.removeBastion(config, serverApi, floatingIPApi, bastion);
        System.out.println();
        bastion = null;
        ClientProceduresRemoval.removeOsSetups(config, securityGroupApi, keyPairApi);
        Utils.printTimestampedMessage(System.out, "\n", "REMOVE-ENV: FINISHED.", "\n");
    }

    private void removeCluster()
    {
        if((bastion == null && !this.initBastionReference()) ||
                Utils.getServerPublicIp(bastion, config.getNetworkName()) == null)
        {
            System.out.println("Bastion not found, skipping cluster deprovision.");
        }
        else
        {
            ClientProcedures.prepareToolComponents(config, tempFolderFile.getAbsolutePath(), flavorApi, true);
            Utils.printTimestampedMessage(System.out, "\n", "REMOVE-CLUSTER: STARTED.", "\n");
            ClientProcedures.updateToolComponentsOnBastion(ssh, config, bastion, tempFolderFile, logsFolderFile);
            System.out.println();
            stopSW(false);
            System.out.println();
            ClientProceduresRemoval.deleteCluster(ssh, /*serverGroupApi, */config, bastion, osAuthOnBastionCommands);
            Utils.printTimestampedMessage(System.out, "\n", "REMOVE-CLUSTER: FINISHED.", "\n");
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



    private void runBastionRoutine(String mode)
    {
        Utils.printTimestampedMessage(System.out, "\n", "Launching this JAR on Bastion...", "\n");
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
        System.out.println("Validation finished.");
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
                        mainClass.imageApi, mainClass.flavorApi, config, mainClass.ssh);
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
                        mainClass.bastion, mainClass.tempFolderFile.getAbsolutePath());
            else if(cmd[0].equals("-ce.h"))
                ClientProcedures.bastionSshConfigCreate(mainClass.ssh, mainClass.bastion, config, mainClass.tempFolderFile.getAbsolutePath());
            else if(cmd[0].equals("-ce.i"))
                ClientProcedures.createSwDisk(mainClass.ssh, config, mainClass.volumeApi,
                        mainClass.bastion, mainClass.volumeAttachmentApi);
            else if(cmd[0].equals("-ce.j"))
                ClientProcedures.attachSwDisk(config, mainClass.bastion, mainClass.volumeApi,
                        mainClass.volumeAttachmentApi);
            else if(cmd[0].equals("-ce.k"))
                ClientProcedures.mountSwDisk(mainClass.ssh, config, mainClass.bastion, mainClass.volumeAttachmentApi);
            else if(cmd[0].equals("-ce.l"))
                ClientProcedures.runSwDiskPreparation(mainClass.ssh, config, mainClass.bastion, false);
            else if(cmd[0].equals("-ce.ll"))
                ClientProcedures.runSwDiskPreparation(mainClass.ssh, config, mainClass.bastion, true);
            else if(cmd[0].equals("-ce.m"))
                ClientProcedures.unmountSwDisk(mainClass.ssh, config, mainClass.bastion);
            else if(cmd[0].equals("-ce.n"))
                ClientProcedures.detachSwDisk(config, mainClass.bastion, mainClass.volumeApi, mainClass.volumeAttachmentApi);
//            else if(cmd[0].equals("-ce.o"))
//                //ClientProcedures.createSwMainVolumeImage(config, mainClass.volumeSnapshotApi);
//                ClientProcedures.createSwMainVolumeImage(config, mainClass.volumeSnapshotApi, mainClass.imageApi, mainClass.serverApi, mainClass.bastion);
        }
        else if(cmd[0].contains("-cc.") && cmd.length == 1)
        {
            config = Configuration.loadConfig();
            mainClass.initBastionReference();
            if(cmd[0].equals("-cc.a"))
                ClientProcedures.prepareToolComponents(config, mainClass.tempFolderFile.getAbsolutePath(), mainClass.flavorApi, true);
            else if(cmd[0].equals("-cc.b"))
                ClientProcedures.updateToolComponentsOnBastion(mainClass.ssh, config, mainClass.bastion, mainClass.tempFolderFile,
                        mainClass.logsFolderFile);
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
            else if(cmd[0].equals("-cc.j"))
            {
                mainClass.runBastionRoutine(Commands.CREATE_CLUSTER.getCommand());
            }
        }
        else if(cmd[0].contains("-r.") && cmd.length == 1)
        {
            mainClass.initBastionReference();
            if(cmd[0].equals("-r.a"))
                ClientProceduresRemoval.deleteCluster(mainClass.ssh, /*serverGroupApi, */config,
                        mainClass.bastion, mainClass.osAuthOnBastionCommands);
            else if(cmd[0].equals("-r.c"))
                ClientProceduresRemoval.removeSwCaches(mainClass.ssh, config, mainClass.volumeApi, mainClass.volumeAttachmentApi,
                        mainClass.bastion, false);
            else if(cmd[0].equals("-r.d"))
                ClientProceduresRemoval.removeBastion(config, mainClass.serverApi, mainClass.floatingIPApi,
                        mainClass.bastion);
            else if(cmd[0].equals("-r.e"))
                ClientProceduresRemoval.removeOsSetups(config, mainClass.securityGroupApi, mainClass.keyPairApi);
        }
        else if(cmd[0].equals("-auth") && cmd.length == 1)
        {
            out.println("\n" + mainClass.osAuthOnBastionCommands + "\n");
        }
        else
        {
            out.println("\nInvalid command.\n");
        }
    }



    public void close() throws IOException
    {
        Closeables.close(novaApi, true);
    }

}
