package no.uit.metapipe.cpouta;

import com.jcraft.jsch.JSch;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;

import java.io.File;

public class BastionRoutine
{

    private BastionRoutine() { }

    static void bastionRoutine(int index, JSch ssh, Configuration config, Server bastion, Server master,
                               VolumeApi volumeApi, VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("\nBastion routine started...\n\n");
        prepareMaster(index != 0, ssh, config, master);
        System.out.println();
        if(index == 2)
        {
            ClientProcedures.updateInstallationBashScripts(ssh, config, bastion, master, false, volumeApi, volumeAttachmentApi);
            launchInstallation(ssh, config, master);
            return;
        }
        System.out.println("Launching final procedures on Bastion...");
//        runMasterRoutine(index == 1, ssh, config, master);
        if(index == 0)
        {
            clusterSetup(ssh, config, master);
        }
        clusterTest(ssh, config, master);
        System.out.println();
        if(index == 0)
        {
            prepareInstallation(ssh, config, master, volumeApi, volumeAttachmentApi);
        }
        else
        {
            ClientProcedures.updateInstallationBashScripts(ssh, config, bastion, master, false, volumeApi, volumeAttachmentApi);
        }
        testInstallation(ssh, config, master);
        System.out.println("\nBastion routine complete.\n\n");
    }

    static void prepareMaster(boolean testOnly, JSch ssh, Configuration config, Server master)
    {
        System.out.println("Started preparing/updating files on master...");
        File arc = new File("arc.tar");
        String commands = "tar -xf arc.tar --overwrite;";
        if(!testOnly)
        {
            commands += "sudo yum install -y java-1.8.0-openjdk-devel;" +
                "sudo chmod 777 " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterSwSetupScript")) + ";" +
                "sudo chmod 777 " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterSwSetupScriptInit")) + ";";
        }
        Utils.sshCopier(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()),
                new String[]{arc.getAbsolutePath()}, "");
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()),
                commands);
        System.out.println("Required files prepared in Master.");
    }

    static void clusterSetup(JSch ssh, Configuration config, Server master)
    {
        System.out.println("Launching Cluster setup on Master...");
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterSwSetupScript")) +
                " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
        System.out.println("Cluster setup on Master complete.\n");
    }

    static void clusterTest(JSch ssh, Configuration config, Server master)
    {
        System.out.println("Launching Cluster test on Master...");
        String commands = "source " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterSwSetupScript")) +
                " test " + Utils.getFileNameFromPath(config.getXternFiles().get("clusterTestScript")) + " 2>&1;" +
                "sleep 1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
        System.out.println("Cluster test complete.\n");
    }

    static void prepareInstallation(JSch ssh, Configuration config, Server master,
                                    VolumeApi volumeApi, VolumeAttachmentApi volumeAttachmentApi)
    {
        System.out.println("Started transfering/preparing installation software...");
        String swPath = "/media/" + config.getSwDiskName() + "/" + config.getInstallationPackedLocation();
        String commands =
                "cd " + swPath + ";" +
                        "source " + config.getInstallationPrepareScript() + " " + config.getSwDiskName() + " 2>&1;";
        ClientProcedures.attachSwDisk(config, master, volumeApi, volumeAttachmentApi);
        ClientProcedures.mountSwDisk(ssh, config, master);
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master, config.getNetworkName()), commands);
        ClientProcedures.unmountSwDisk(ssh, config, master);
        ClientProcedures.detachSwDisk(config, master, volumeApi, volumeAttachmentApi);
        System.out.println("Finished transfering/preparing installation software.\n");
    }

    static void testInstallation(JSch ssh, Configuration config, Server master)
    {
        System.out.println("Started validating installation software...");
        String commands =
                "cd " + config.getInstallationUnpackedLocation() + ";" +
                        "source " + config.getInstallationTestScript() + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
        System.out.println("Finished validating installation software.\n");
    }

    static void launchInstallation(JSch ssh, Configuration config, Server master)
    {
        System.out.println("Launching installation software...");
        String commands =
                "cd " + config.getInstallationUnpackedLocation() + ";" +
                        "source " + config.getInstallationLaunchScript() + " 2>&1;";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
    }

    static void runMasterRoutine(boolean testOnly, JSch ssh, Configuration config, Server master)
    {
        System.out.println("Launching Master routine...");
        String onlyTest;
        if(testOnly)
        {
            onlyTest = MainClass.testString;
        }
        else
        {
            onlyTest = "";
        }
        String commands =
                "java -jar $(pwd)/Metapipe-cPouta.jar _master-routine=" + onlyTest + ";";
        Utils.sshExecutor(ssh, config.getUserName(),
                Utils.getServerPrivateIp(master,config.getNetworkName()), commands);
    }

}
