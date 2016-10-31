package no.uit.metapipe.cpouta;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static java.lang.Thread.sleep;

public class MasterRoutine
{

    private MasterRoutine() { }

    static void masterRoutine(Configuration config, boolean testOnly)
    {
        if(!testOnly)
        {
            System.out.println("Launching final procedures on Master...");
            masterRoutine_clusterSetup();
            System.out.println();
        }
        masterRoutine_clusterTest(config);
        System.out.println("Master routine complete.\n\n");
    }

    static void masterRoutine_clusterSetup()
    {
        System.out.println("Launching Cluster setup on Master...");
        String commands = "source _setup_cluster.sh;";
        Utils.localExecutor(commands, true);
        System.out.println("Cluster setup on Master complete.\n");
    }

    static void masterRoutine_clusterTest(Configuration config)
    {
        System.out.println("Launching Cluster test on Master...");
        String commands = "source _setup_cluster.sh test " +
                Utils.getFileNameFromPath(config.getXternFiles().get("clusterTestScript")) + ";" +
                "sleep 1;";
        Utils.localExecutor(commands, true);
        System.out.println("Cluster test complete.\n");
    }

    static void masterRoutine_clusterSetup_old(Configuration config)
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

    static void masterRoutine_clusterTest_old(Configuration config)
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

}
