package no.uit.metapipe.cpouta;

import com.jcraft.jsch.JSch;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.slf4j.helpers.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BastionRoutine
{

    private BastionRoutine() { }

    static void bastionRoutine(String index, JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nBastion routine started with argument '" + index + "'\n\n");
        String execMasterIndex = MainClass.Commands.EXEC_MASTER.getCommand().replace(">", "");
        if(index.equals(MainClass.Commands.CREATE_CLUSTER.getCommand()))
        {
            prepareMaster(ssh, config, master);
            updateToolComponentsOnMaster(ssh, config, master);
            setupCluster(ssh, config, master);
            testCluster(ssh, config, master);
            setupSwOnCluster(ssh, config, master);
            testSW(ssh, config, master);
        }
        else if(index.startsWith(execMasterIndex))
        {
            executeOnMaster(ssh, config, master, index.replace(execMasterIndex, "").trim());
        }
        else
        {
            if(index.equals(MainClass.Commands.SW_LAUNCH_DEV.getCommand()) || index.equals(MainClass.Commands.SW_STOP_DEV.getCommand()) ||
                index.equals(MainClass.Commands.TEST_DEV.getCommand()))
            {
                updateToolComponentsOnMaster(ssh, config, master);
            }
            if(index.equals(MainClass.Commands.SW_LAUNCH.getCommand()) || index.equals(MainClass.Commands.SW_LAUNCH_DEV.getCommand()))
            {
                stopSW(ssh, config, master);
                launchSW(ssh, config, master);
            }
            else if(index.equals(MainClass.Commands.SW_STOP.getCommand()) || index.equals(MainClass.Commands.SW_STOP_DEV.getCommand()))
            {
                stopSW(ssh, config, master);
            }
            else if(index.equals(MainClass.Commands.TEST.getCommand()) || index.equals(MainClass.Commands.TEST_DEV.getCommand()))
            {
                stopSW(ssh, config, master);
                testCluster(ssh, config, master);
                testSW(ssh, config, master);
            }
            else
            {
                System.out.println("\nInvalid Bastion routine argument.");
            }
            return;
        }
        System.out.println("\nBastion routine complete.\n");
    }

    static void prepareMaster(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nStarted preparing Master...");
        String commands = "sudo yum install -y java-1.8.0-openjdk-devel;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()),
                commands);
        System.out.println("Preparation on Master finished.\n");
    }

    static void updateToolComponentsOnMaster(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nStarted preparing/updating files on master...");
        File arc = new File("arc.tar");
        String swPath = config.getNfsSwMainVolumeMount() + "/" + config.getSwFilesDirName();
        String commands = "rm -f arc.tar;" + "rm -r -f temp;";
        for(String s : config.getXternFiles().values())
        {
            commands += "rm -r -f " + Utils.getFileNameFromPath(s) + ";";
        }
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()),
                commands);
        Utils.sshCopier(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()),
                new String[]{arc.getAbsolutePath()}, "");
        commands = "tar -xf arc.tar --overwrite 2>&1;";
        commands += "sudo rm " + swPath + "/*.sh;" +
                "sudo cp " + config.getXternFiles().get("sw") + "/*.sh " + swPath + " ;" +
                "sudo chmod 777 " + swPath + "/*.sh;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()),
                commands);
        System.out.println("Required files prepared in Master.\n");
    }

    static void setupCluster(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nLaunching Cluster setup on Master...");
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("disablePasswordAuth")) + " 2>&1;";
        commands += "source " + Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScript")) + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()), commands);
        System.out.println("Cluster setup on Master complete.\n");
    }

    static void testCluster(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nLaunching Cluster test on Master...");
        String commands =
                "source " + Utils.getFileNameFromPath(config.getXternFiles().get("sparkSetupScript")) +
                    " test " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterTestScript")) + " 2>&1;" +
                "sleep 1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
        System.out.println("Cluster test complete.\n");
    }

    static void setupSwOnCluster(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nStarted transfering/preparing SW...");
        String commands =
                "cd " + config.getNfsSwMainVolumeMount() + "/" + config.getSwFilesDirName() + ";" +
                "source " + config.getSwPrepareScript() + " 2>&1";
        commands += " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()), commands);
        System.out.println("Finished transfering/preparing SW.\n");
    }

    static void testSW(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nStarted validating SW...");
        String commands =
                "cd " + config.getNfsSwMainVolumeMount() + "/" + config.getSwFilesDirName() + ";" +
                "source " + config.getSwTestScript() + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
        System.out.println("Finished validating SW.\n");
    }

    static void launchSW(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nLaunching SW...");
        String commands =
                "cd " + config.getNfsSwMainVolumeMount() + "/" + config.getSwFilesDirName() + ";" +
                "source " + config.getSwLaunchScript() + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
    }

    static void stopSW(JSch ssh, Configuration config, Server master)
    {
        System.out.println("\nStopping SW...");
        String commands =
                "cd " + config.getNfsSwMainVolumeMount() + "/" + config.getSwFilesDirName() + ";" +
                "source " + config.getSwStopScript() + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
    }

    static void executeOnMaster(JSch ssh, Configuration config, Server master, String tempScriptPath)
    {
        System.out.println("\nExecuting commands on Master...");
//        String commands =
//                "source " + tempScriptPath + " ;" +
//                "rm " + tempScriptPath + " ;";
//        Utils.sshCopier(ssh, config.getUserName(),
//                Utils.getServerPrivateIp(master, config.getNetworkName()),
//                new String[]{tempScriptPath}, tempScriptPath.substring(0, tempScriptPath.lastIndexOf("/")), false, true);
//        Utils.sshExecutor(ssh, config.getUserName(),
//                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
//        new File(tempScriptPath.replaceFirst("~/", System.getProperty("user.home") + "/")).delete();
        String commands =
                "cd " + config.getNfsSwMainVolumeMount() + "/" + config.getSwFilesDirName() + ";" +
                "source " + config.getSwInitScript() + " 2>&1;" + "cd ~; ";
        try
        {
            commands += new String(
                    Files.readAllBytes(Paths.get(tempScriptPath.replaceFirst("~/", System.getProperty("user.home") + "/"))),
                    "UTF-8");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return;
        }
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
    }

//    static void runMasterRoutine(boolean testOnly, JSch ssh, Configuration config, Server master)
//    {
//        System.out.println("Launching Master routine...");
//        String onlyTest;
//        if(testOnly)
//        {
//            onlyTest = MainClass.stringTest;
//        }
//        else
//        {
//            onlyTest = "";
//        }
//        String commands =
//                "java -jar $(pwd)/Metapipe-cPouta.jar _master-routine=" + onlyTest + ";";
//        Utils.sshExecutor(ssh, config.getUserName(),
//                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
//    }

}
