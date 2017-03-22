package no.uit.metapipe.cpouta;

public class MasterRoutine
{
    private MasterRoutine() { }

//    static void masterRoutine(Configuration config, boolean testOnly)
//    {
//        if(!testOnly)
//        {
//            System.out.println("Launching final procedures on Master...");
//            masterRoutine_clusterSetup();
//            System.out.println();
//        }
//        masterRoutine_clusterTest(config);
//        System.out.println("Master routine complete.\n\n");
//    }
//
//    static void masterRoutine_clusterSetup()
//    {
//        System.out.println("Launching Cluster setup on Master...");
//        String commands = "source _setup_cluster.sh;";
//        Utils.localExecutor(commands, true);
//        System.out.println("Cluster setup on Master complete.\n");
//    }
//
//    static void masterRoutine_clusterTest(Configuration config)
//    {
//        System.out.println("Launching Cluster test on Master...");
//        String commands = "source _setup_cluster.sh test " +
//                Utils.getFileNameFromPath(config.getXternFiles().get("clusterTestScript")) + ";" +
//                "sleep 1;";
//        Utils.localExecutor(commands, true);
//        System.out.println("Cluster test complete.\n");
//    }
}
