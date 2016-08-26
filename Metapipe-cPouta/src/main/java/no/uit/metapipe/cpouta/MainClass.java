package no.uit.metapipe.cpouta;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.inject.Module;
import com.jcraft.jsch.*;
import jline.console.ConsoleReader;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.net.domain.IpProtocol;
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
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.System.exit;

public class MainClass implements Closeable
{

    private static Configuration config = null;

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

    // RUN JAR IN TERMINAL: java -jar $(pwd)/Metapipe-cPouta.jar
    private MainClass(String userName, String password, String idRsaKeyFileName)
    {

        System.out.print("\nStarting execution... \n\n");

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
                    .endpoint("https://pouta.csc.fi:5001/v2.0")
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
        //tempFolder = decodedPath + "temp";
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
        System.out.print("\n");
        Yaml yaml = new Yaml();
        final String[] commands = new String[] {
                /*0*/ "help", /*1*/ "quit", /*2*/ "exit",
                /*3*/ "create-all", /*4*/ "create-cluster", /*5*/ "remove-all", /*6*/ "remove-cluster",
                /*7*/ "create-all-image-configured", /*8*/ "create-cluster-image-configured",
                /*9*/ "scale-up", /*10*/ "scale-down"
        };
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
                "This tool can create Spark cluster environment on CSC, or remove the existing one.\n" +
                "The current version supports only one cluster per time, and doesn't support scale-up/down." +
                "Required parameters are taken from the file 'config.yml' in the same folder with this Jar.\n" +
                "Be sure that config.yml is configured correctly.\n";
        final String helpOps =
                "Commands:\n" +
                "help: This message.\n" +
                "quit/exit: Exit.\n" +
                "create-all: Create the environment from scratch - CSC settings, bastion, cluster" +
                        "(given that none of them exist).\n" +
                "create-cluster: Create only a new cluster " +
                    "(given that procedures with CSC settings and bastion were done before, and cluster doesn't exist).\n" +
                "remove-all: Remove the environment, cleanup everything.\n" +
                "remove-cluster: Remove only existing cluster and keep CSC settings and bastion for future use.\n" +
                "(not implemented yet) create-all-image-configured: Same as create-all, " +
                        "but creates everything from previously saved configured images, so all software setups are skipped.\n" +
                "(not implemented yet) create-cluster-image-configured: Same as create-cluster, " +
                        "but creates master/nodes from previously saved configured images, so all software setups are skipped.\n" +
                "(not implemented yet) scale-up: Scales up existing cluster with given number of instances.\n" +
                "(not implemented yet) scale-down: Scales down existing cluster with given number of instances.\n";

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
            exit(1);
        }
        else
        {
            String usr = null, pswd = null;
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
                    mainClass.bastionRoutine();
                    return;
                }
                else if(arg.trim().equals("_master-routine"))
                {
                    System.out.println("===== MASTER ROUTINE =====");
                    mainClass = new MainClass(usr, pswd, null);
                    mainClass.masterRoutine();
                    return;
                }
            }
            if(usr == null || pswd == null || usr.isEmpty() || pswd.isEmpty())
            {
//                System.out.println("Check the correctness of OpenStack username and password arguments!");
                System.out.println(helpLaunch);
                exit(1);
            }
            System.out.println(help);
            InputStream inStream = new FileInputStream(FileDescriptor.in);
            ConsoleReader reader = null;
            try
            {
                reader = new ConsoleReader(programName, inStream, System.out, null);
                reader.setPrompt("> ");
                reader.addCompleter(new FileNameCompleter());
                reader.addCompleter(new StringsCompleter(Arrays.asList(commands)));
                String line;
                PrintWriter out = new PrintWriter(reader.getOutput());
                out.println(helpOps);
                while ((line = reader.readLine()) != null)
                {
                    String[] cmd = line.split("\\s+");
                    if(cmd.length > 1)
                    {
                        out.println("One command per time please.");
                    }
                    else
                    {
                        if(cmd[0].equalsIgnoreCase(commands[0]))
                        {
                            out.println(help);
                            out.println(helpOps);
                        }
                        else if (line.equalsIgnoreCase(commands[1]) || line.equalsIgnoreCase(commands[2]))
                        {
                            break;
                        }
                        else if(cmd[0].equalsIgnoreCase(commands[3]))
                        {
                            System.out.println("===== MAIN ROUTINE =====");
                            mainClass = new MainClass(usr, pswd, "id_rsa");
                            mainClass.createAll();
                        }
                        else if(cmd[0].equalsIgnoreCase(commands[4]))
                        {
                            System.out.println("===== MAIN ROUTINE =====");
                            mainClass = new MainClass(usr, pswd, "id_rsa");
                            mainClass.createCluster();
                        }
                        else if(cmd[0].equalsIgnoreCase(commands[5]))
                        {
                            mainClass = new MainClass(usr, pswd, "id_rsa");
                            mainClass.removeAll();
                        }
                        else if(cmd[0].equalsIgnoreCase(commands[6]))
                        {
                            mainClass = new MainClass(usr, pswd, "id_rsa");
                            mainClass.removeCluster();
                        }
                        else
                        {
                            out.println("Invalid command");
                        }
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
    }



    private void createCluster()
    {
        if(bastion == null)
        {
            this.initBastionReference();
            System.out.print("\n");
        }
        Procedures.clusterServerGroupCreate(config, serverGroupApi);
        System.out.print("\n");
        Procedures.createClusterVarsFile(config, tempFolder);
        System.out.print("\n");
        Procedures.updateAmbariShellCommandFile(config);
        System.out.print("\n");
        Procedures.transferRequiredFiles2Bastion(ssh, config, bastion, tempFolder);
        System.out.print("\n");
        Procedures.bastionClusterProvisionExecute(ssh, config, bastion, cscAuthOnBastionCommands);
        System.out.print("\n");
        Procedures.openAdminAccess(config, securityGroupApi);
        System.out.print("\n");
        Procedures.generateAmbariLink(config.getClusterName(),
                Utils.getServerPublicIp(getMasterReference(config.getClusterName()), config.getNetworkName()));
        System.out.print("\n");
        this.runBastionRoutine();
        System.out.print("\n");
    }

    private void createAll()
    {
        Procedures.bastionSecGroupCreate(securityGroupApi, config);
        System.out.print("\n");
        Procedures.bastionKeyPairCreate(ssh, sshFolder, config.getBastionKeyName(), keyPairApi);
        System.out.print("\n");
        this.bastion = Procedures.bastionServerCreate(serverApi, imageApi, flavorApi, config);
        System.out.print("\n");
        Procedures.bastionIpAllocate(config, floatingIPApi, bastion);
        System.out.print("\n");
        Procedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        Procedures.bastionPackagesInstall(ssh, bastion, config, serverApi);
        System.out.print("\n");
        Procedures.bastionAnsibleInstall(ssh, config, Utils.getServerPublicIp(bastion, config.getNetworkName()));
        System.out.print("\n");
        Procedures.bastionClusterKeyCreate(ssh, config, keyPairApi, bastion, tempFolder);
        System.out.print("\n");
        Procedures.bastionSshConfigCreate(ssh, bastion, config, tempFolder);
        System.out.print("\n");
        this.createCluster();
    }

    private void removeAll()
    {
        if(bastion == null)
        {
            this.initBastionReference();
            System.out.print("\n");
        }
        this.removeCluster();
        System.out.print("\n");
        ProceduresRemoval.removeBastion(config, serverApi, floatingIPApi, bastion);
        System.out.print("\n");
        bastion = null;
        ProceduresRemoval.removeOsSetups(config, securityGroupApi, keyPairApi);
        System.out.print("\n");
    }

    private void removeCluster()
    {
        if(bastion == null)
        {
            this.initBastionReference();
            System.out.print("\n");
        }
        ProceduresRemoval.deleteCluster(ssh, serverGroupApi, config, bastion, cscAuthOnBastionCommands);
        System.out.print("\n");
    }



    private void initBastionReference()
    {
        System.out.println("Creating Bastion java reference...");
        ImmutableList<Server> tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            if(s.getName().equals(config.getBastionMachineName()))
            {
                bastion = serverApi.get(s.getId());
                System.out.println("Bastion java reference created.");
                return;
            }
        }
        System.out.println("Bastion host was not found. Can't continue.");
        exit(1);
    }

    private Server getMasterReference(String clusterName)
    {
        System.out.println("Creating Bastion java reference...");
        ImmutableList<Server> tempServerList = serverApi.listInDetail().get(0).toList();
        for(Server s : tempServerList)
        {
            if(s.getName().equals(clusterName + "-master"))
            {
                System.out.println("Master for " + clusterName + " found.");
                return s;
            }
        }
        System.out.println("Master host was not found. Can't continue.");
        exit(1);
        return null;
    }



    private void runBastionRoutine()
    {
        System.out.println("Launching this JAR on Bastion...");
        String commands =
                "java -jar $(pwd)/Metapipe-cPouta.jar " +
                        "username=" + this.userName + " password=" + this.password + " _bastion-routine;";
        Utils.sshExecutor(ssh, config.getUserName(), Utils.getServerPublicIp(bastion, config.getNetworkName()), commands);
        System.out.println("This JAR has finished all procedures on Bastion.");
    }

    private void bastionRoutine()
    {
        System.out.println("Launching final procedures on Bastion...");
        this.bastionRoutine_prepareFilesOnMaster();
        System.out.print("\n");
        this.bastionRoutine_runMasterRoutine();
        System.out.println("\nCLUSTER CREATION COMPLETE.\n\n");
    }

    private void bastionRoutine_prepareFilesOnMaster()
    {
        File arc = new File("arc.tar");
        String commands = "tar -xvf arc.tar --overwrite;";
        Utils.sshCopier(ssh, config.getUserName(),
                Utils.getServerPrivateIp(getMasterReference(config.getClusterName()), config.getNetworkName()),
                new String[]{arc.getAbsolutePath()}, "");
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(getMasterReference(config.getClusterName()),config.getNetworkName()),
                commands);
        System.out.println("Required files prepared in Master.");
    }

    private void bastionRoutine_runMasterRoutine()
    {
        String commands =
                "java -jar $(pwd)/Metapipe-cPouta.jar _master-routine;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(getMasterReference(config.getClusterName()),config.getNetworkName()),
                commands);
        System.out.println("Required files prepared in Master.");
    }

    private void masterRoutine()
    {
        System.out.println("Launching final procedures on Master...");
        this.masterRoutine_clusterSetup();
        System.out.print("\n");
        this.masterRoutine_clusterTest();
        System.out.println("\nCluster test complete.\n");
    }

    private void masterRoutine_clusterSetup()
    {
        System.out.println("Launching Cluster setup on Master...");
        String commands =
            "sudo yum install -y java-1.8.0-openjdk-devel;" +
            "sleep 1;" +
            "java -jar " + config.getXternFiles().get("ambariShellJar") + " --ambari.server=localhost --ambari.port=8080 --ambari.user=admin --ambari.password=admin << EOF \n" +
            "script " + config.getXternFiles().get("ambariShellCommands") + "\nexit\n" + "EOF\n\n" +
            "echo 'Ambari password changed.';" +
            "sleep 10;" +
            "while ! type 'hadoop' > /dev/null || ! hadoop || ! hadoop fs -test -d /user; do\n" +
            "  sleep 10;" +
            "done;" +
            "echo 'Hadoop FS: /user created.';" +
            "sudo -u hdfs hadoop fs -mkdir /user/" + config.getUserName() + ";" +
            "while [[ $? -ne 0 ]] && ! hadoop fs -test -d /user/" + config.getUserName() + "; do\n" +
            "  sleep 10;" +
            "  sudo -u hdfs hadoop fs -mkdir /user/" + config.getUserName() + ";" +
            "done;" +
            "echo 'Hadoop FS: /user/" + config.getUserName() + " created.';" +
            "sudo -u hdfs hadoop fs -chown -R " + config.getUserName() + " /user/" + config.getUserName() + ";" +
            "echo 'Hadoop FS: owner changed to " + config.getUserName() + "';" +
            "sleep 1;";
        Utils.localExecutor(commands);
        System.out.println("Cluster setup on Master complete.\n");
    }

    private void masterRoutine_clusterTest()
    {
        System.out.println("Launching Cluster test on Master...");
        String commands =
            "\n" + "echo 'RUNNING TEST SCRIPT ON THE MASTER MACHINE';" + "\n" +
            "sleep 1;" +
            "/usr/hdp/current/spark-client/bin/pyspark " + config.getXternFiles().get("clusterTestScript") + ";" +
            "\n" + "echo 'RUNNING TEST SCRIPT ON THE ENTIRE CLUSTER';" + "\n" +
            "sleep 1;" +
            "/usr/hdp/current/spark-client/bin/pyspark --master yarn cluster_test.py " + config.getXternFiles().get("clusterTestScript") + ";" +
            "sleep 1;";
        Utils.localExecutor(commands);
        System.out.println("Cluster test on Master complete.\n");
    }



    static void addIpMasterAccess(String ip, SecurityGroupApi securityGroupApi)
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
            exit(1);
        }
        try
        {
            securityGroupApi.createRuleAllowingCidrBlock(masterGroup.getId(),
                    Ingress.builder().fromPort(8080).toPort(8080).ipProtocol(IpProtocol.TCP).build(),
                    ip + "/32");
        }
        catch (IllegalStateException e)
        {
            System.out.println("Security rule for accessing Master already exists!");
        }
        System.out.println("Admin IP address added: " + ip);
    }

    static void removeIpMasterAccess(String ip, SecurityGroupApi securityGroupApi)
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
            exit(1);
        }
        for(SecurityGroupRule rule : masterGroup.getRules())
        {
            if(rule.getIpRange().contains(ip))
            {
                securityGroupApi.deleteRule(rule.getId());
                System.out.println("Admin IP address removed: " + ip);
                return;
            }
        }
        System.out.println("Provided IP address not registered as cluster admin! " + ip);
    }



    public void close() throws IOException
    {
        Closeables.close(novaApi, true);
    }

}
