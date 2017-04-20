package no.uit.metapipe.cpouta;

import com.jcraft.jsch.*;
import com.jcraft.jsch.KeyPair;
import org.jclouds.openstack.nova.v2_0.domain.*;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.v2_0.domain.Resource;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;

final class Utils
{

    private Utils() { }

    static String getCurrentDateTimeString()
    {
        return (new SimpleDateFormat("HH:mm:ss, dd.MM.yyyy.")).format(Calendar.getInstance().getTime());
    }

    static void printTimestampedMessage(PrintStream out, String prefix, String text, String postfix)
    {
        out.println(prefix + getCurrentDateTimeString() + ": " + text + postfix);
    }

    static String localExecutor(String commands, boolean log)
    {
        Process p;
        String output = "";
        try
        {
            if(log)
            {
                p = new ProcessBuilder("bash", "-c", commands)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            }
            else
            {
                p = new ProcessBuilder("bash", "-c", commands).start();
            }
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine())!= null)
            {
                output += line + System.getProperty("line.separator");
                if(log)
                {
                    System.out.println(line);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return output;
    }

    static void sshExecutor(JSch ssh, String userName, String ip, String aBigCommandString)
    {
        Session session;
        Properties props = new Properties();
        ChannelShell channel;
        BufferedReader in;
        PrintStream commandsStream;
        String suffix = "";
        System.out.println("Starting SSH session to... " + userName + "@" + ip);
        aBigCommandString = aBigCommandString.trim();
        try {
            session = ssh.getSession(userName, ip);
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.connect();
            channel = (ChannelShell)session.openChannel("shell");
            in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            //channel.setPtyType(aBigCommandString);
            channel.connect();
            System.out.println("Executing commands... ");
            if(!aBigCommandString.endsWith(";"))
            {
                suffix = ";";
            }
            suffix += " exit;";
            commandsStream = new PrintStream(channel.getOutputStream(), true);
            commandsStream.println(aBigCommandString + suffix);
            String msg;
            while((msg = in.readLine()) != null)
            {
                System.out.println(msg);
            }
            System.out.println();
            channel.disconnect();
            session.disconnect();
            System.out.println("SSH session over.");
        }
        catch (JSchException e)
        {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            if(errors.toString().contains("Connection refused") || errors.toString().contains("No route to host") ||
                    errors.toString().contains("Auth fail") || errors.toString().contains("Connection reset"))
            {
                try
                {
                    sleep(1000);
                }
                catch (InterruptedException e1)
                {
                    e.printStackTrace();
                }
                System.out.println("Connection failed, trying to connect again...");
                sshExecutor(ssh, userName, ip, aBigCommandString);
            }
            else
            {
                e.printStackTrace();
                exit(1);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            exit(1);
        }
    }

//    static String mergeStreams4OneCommand(String command)
//    {
//        String res = command;
//        if(res.endsWith(" ;"))
//        {
//            res.replace(" ;", "");
//        }
//        else if(res.endsWith(";"))
//        {
//            res.replace(";", "");
//        }
//        //res = res + " 2>&1;";
//        res = res + ";";
//        return res;
//    }
//
//    static String mergeStreams4ManyCommands(String commands)
//    {
//        List<String> commandsList = Arrays.asList(commands.split(";"));
//        String res = "";
//        for(String s : commandsList)
//        {
//            res += mergeStreams4OneCommand(s.trim());
//        }
//        return res;
//    }

    static void sshCopier(JSch ssh, String userName, String ip, String[] src, String dest)
    {
        sshCopier(ssh, userName, ip, src, dest, true, true);
    }

    static void sshCopier(JSch ssh, String userName, String ip, String[] src, String dest, boolean startAtHome, boolean replace)
    {
        Session session;
        System.out.println("Starting SSH session to... " + userName + "@" + ip);
        try
        {
            File f;
            String tempDir;
            String commands;
            String[] folders;
            Properties props = new Properties();
            session = ssh.getSession(userName, ip);
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.connect();
            ChannelSftp channel = (ChannelSftp)session.openChannel("sftp");
            channel.connect();
            System.out.println("Copying files... ");
            System.out.println("SrcArg: " + String.join(", ", src));
            System.out.println("DestArg: " + dest);
            if(dest.startsWith("~"))
            {
                dest = channel.getHome().substring(1) + "/" + dest.substring(2);
            }
            folders = dest.split("/");
            String startingPlace;
            if(startAtHome)
                startingPlace = channel.getHome() + "/";
            else
                startingPlace = "/";
            channel.cd(startingPlace);
            for (String folder : folders)
            {
                if (folder.length() > 0)
                {
                    try
                    {
                        channel.cd(folder);
                    }
                    catch ( SftpException e )
                    {
                        channel.mkdir(folder);
                        channel.cd(folder);
                    }
                }
            }
            channel.cd(startingPlace);
            for(String s : src)
            {
                if(s.startsWith("~"))
                {
                    s = System.getProperty("user.home") + "/" + s.substring(2);
                }
                System.out.println(s);
                f = new File(s);
                if(!f.isDirectory())
                {
                    System.out.println("Copying file: " + s + " to " + startingPlace + dest + "; Replace: " + replace);
                    if(replace)
                        channel.put(s, startingPlace + dest, ChannelSftp.OVERWRITE);
                    else
                        try
                        {
                            channel.lstat(dest + "/" + f.getName());
                            System.out.println(f.getName() + ": File exists! Not replaced");
                        }
                        catch(Exception e)
                        {
                            channel.put(f.getAbsolutePath(), dest);
                        }
                }
                else
                {
                    tempDir = startingPlace + dest + "/" + f.getName();
                    try
                    {
                        channel.mkdir(tempDir);
                    }
                    catch (Exception e)
                    {
                        System.out.println(tempDir + ": old directory exists!");
                        if(replace)
                        {
                            System.out.println(tempDir + ": Starting second SSH channel for recursive deletion...");
                            commands = "rm -rv " + tempDir + ";";
                            sshExecutor(ssh, userName, ip, commands);
                            System.out.println("Recursive deletion complete.");
                            channel.mkdir(startingPlace + dest + "/" + f.getName());
                        }
                    }
                    sshCopierFolderHelper(channel, f, tempDir, replace);
                }
            }
            channel.disconnect();
            session.disconnect();
            System.out.println("SSH session over.");
        }
        catch (JSchException e)
        {
            e.printStackTrace();
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            if(errors.toString().contains("Connection refused") || errors.toString().contains("No route to host"))
            {
                try
                {
                    sleep(1000);
                }
                catch (InterruptedException e1)
                {
                    e.printStackTrace();
                }
                sshCopier(ssh, userName, ip, src, dest);
            }
            else
            {
                exit(1);
            }
        }
        catch (SftpException e)
        {
            e.printStackTrace();
            exit(1);
        }
    }

    private static void sshCopierFolderHelper(ChannelSftp channel, File f, String dest, boolean replace) throws SftpException
    {
        ArrayList<File> list = new ArrayList<File>(Arrays.asList(f.listFiles()));
        for (File item : list)
        {

            if (!item.isDirectory())
            {
                System.out.println("Copying file: " + item.getAbsolutePath() + " to " + dest + "; Replace: " + replace);
                if(replace)
                    channel.put(item.getAbsolutePath(), dest, ChannelSftp.OVERWRITE);
                else
                    try
                    {
                        channel.lstat(dest + "/" + item.getName());
                        System.out.println(item.getName() + ": File exists! Not replaced");
                    }
                    catch(Exception e)
                    {
                        channel.put(item.getAbsolutePath(), dest);
                    }
            }
            else if (!".".equals(f.getName()) || "..".equals(f.getName()))
            {
                System.out.println("Copying directory: " + dest + "/" + item.getName());
                channel.mkdir(dest + "/" + item.getName());
                sshCopierFolderHelper(channel, item, dest + "/" + item.getName(), replace); // Enter found folder on server to read its contents and create locally.
            }

        }
    }

    static String getFileNameFromPath(String path)
    {
        return new File(path).getName();
    }

    static String getServerPrivateIp(Server s, String networkName)
    {
        Object[] a = s.getAddresses().get(networkName).toArray();
        if(a.length > 0)
        {
            return ((Address)a[0]).getAddr();
        }
        return null;
    }

    static String getServerPublicIp(Server s, String networkName)
    {
        Object[] a = s.getAddresses().get(networkName).toArray();
        if(a.length > 1)
        {
            return ((Address)a[1]).getAddr();
        }
        return null;
    }

    // newValue = null: for removing the line with the value
    static void updateFileValue(String filePath, String valueName, String newValue, String separatedBy, boolean firstOnly)
    {
        updateFileValue(filePath, valueName, newValue, separatedBy, firstOnly, false);
    }
    static void updateFileValue(String filePath, String valueName, String newValue, String separatedBy, boolean firstOnly, boolean silent)
    {
        try
        {
            List<String> lines;
            List<String> newLines = new ArrayList<String>();
            File f = new File(filePath);
            int replacedCount = 0;
            lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
            for(String line: lines)
            {
                if(line.contains(valueName) && !line.startsWith("#") && !(firstOnly && replacedCount > 0))
                {
                    if(newValue != null)
                    {
                        newLines.add(updateFileLine(line, valueName, newValue, separatedBy));
                        replacedCount++;
                    }
                }
                else
                {
                    newLines.add(line);
                }
            }
            if(newValue == null)
            {
                newValue = "";
            }
            Files.write(f.toPath(), newLines, Charset.defaultCharset());
            if(!silent)
            {
                System.out.println("File '" + filePath + "' updated: '" + valueName + "' changed to '" + newValue + "'.");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    static void updateYamlFileValue(String filePath, String valueName, String newValue)
    {
        updateFileValue(filePath, valueName, newValue, ": ", false);
    }

    static String updateFileLine(String line, String valueName, String newValue, String separatedBy)
    {
        return line.substring(0, line.indexOf(valueName)) + valueName + separatedBy + String.valueOf(newValue);
    }

    static String updateYamlFileLine(String line, String valueName, String newValue)
    {
        return updateFileLine(line, valueName, newValue, ": ");
    }

    static void addNewListFileEntry(String filePath, String list, String newEntry)
    {
        try
        {
            List<String> lines;
            List<String> newLines = new ArrayList<String>();
            File f = new File(filePath);
            lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
            for(String line: lines)
            {
                newLines.add(line);
                if(line.contains(list))
                {
                    newLines.add(line.substring(0, line.lastIndexOf(list)) + "  - " + newEntry);
                }
            }
            Files.write(f.toPath(), newLines, Charset.defaultCharset());
            System.out.println("Config file updated: '" + newEntry + "', new entry in '" + list + "'.");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    static boolean objectHasContents(Object object)
    {
        if(object instanceof String)
        {
            return object != null && !((String) object).isEmpty();
        }
        else if(object instanceof Map)
        {
            return object != null && !((Map) object).isEmpty();
        }
        else if(object instanceof List)
        {
            return object != null && !((List) object).isEmpty();
        }
        else
        {
            return object != null;
        }
    }

    static Object objectValidate(Object object, String errorMessage)
    {
        if(!objectHasContents(object))
        {
            objectInvalidAction(errorMessage);
        }
        return object;
    }

    static void objectInvalidAction(String errorMessage)
    {
        System.out.println("\n" + errorMessage + "\n");
        exit(1);
    }

    static String stringValidate(String object, String errorMessage)
    {
        return (String)objectValidate(object, errorMessage);
    }

    static Integer intValidate(Integer object, String errorMessage, boolean canBeZero)
    {
        if(canBeZero)
        {
            return (Integer)objectValidate(object, errorMessage);
        }
        if(object == 0)
        {
            objectInvalidAction(errorMessage);
        }
        return object;
    }

    static void createNewKeyPair(String keyName, String path, JSch ssh)
    {
        String privateKey = path + "/" + keyName;
        String pubKey = path + "/" + keyName + ".pub";
        File privateKeyFile = new File(privateKey);
        File pubKeyFile = new File(pubKey);
        if(new File(privateKey).exists() && new File(pubKey).exists())
        {
            System.out.println("Keys already exist.");
        }
        else
        {
            System.out.println(privateKey);
            System.out.println("Generating new keys...");
            try {
                if(!new File(path).exists())
                {
                    Files.createDirectories(Paths.get(path));
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
    }

    static Flavor getFlavorByName(FlavorApi flavorApi, String name)
    {
        for(Resource f : flavorApi.list().get(0))
        {
            if(f.getName().equals(name))
            {
                return  flavorApi.get(f.getId());
            }
        }
        return null;
    }

    // https://ip2cidr.com/
    // http://stackoverflow.com/questions/33443914/how-to-convert-ip-address-range-to-cidr-in-java
    // http://stackoverflow.com/questions/5020317/in-java-given-an-ip-address-range-return-the-minimum-list-of-cidr-blocks-that
    static List<String> range2cidrlist( String startIp, String endIp ) {
        long start = ipToLong(startIp);
        long end = ipToLong(endIp);

        ArrayList<String> pairs = new ArrayList<String>();
        while ( end >= start ) {
            byte maxsize = 32;
            while ( maxsize > 0) {
                long mask = CIDR2MASK[ maxsize -1 ];
                long maskedBase = start & mask;

                if ( maskedBase != start ) {
                    break;
                }

                maxsize--;
            }
            double x = Math.log( end - start + 1) / Math.log( 2 );
            byte maxdiff = (byte)( 32 - Math.floor( x ) );
            if ( maxsize < maxdiff) {
                maxsize = maxdiff;
            }
            String ip = longToIP(start);
            pairs.add( ip + "/" + maxsize);
            start += Math.pow( 2, (32 - maxsize) );
        }
        return pairs;
    }
    private static final int[] CIDR2MASK = new int[] { 0x00000000, 0x80000000,
            0xC0000000, 0xE0000000, 0xF0000000, 0xF8000000, 0xFC000000,
            0xFE000000, 0xFF000000, 0xFF800000, 0xFFC00000, 0xFFE00000,
            0xFFF00000, 0xFFF80000, 0xFFFC0000, 0xFFFE0000, 0xFFFF0000,
            0xFFFF8000, 0xFFFFC000, 0xFFFFE000, 0xFFFFF000, 0xFFFFF800,
            0xFFFFFC00, 0xFFFFFE00, 0xFFFFFF00, 0xFFFFFF80, 0xFFFFFFC0,
            0xFFFFFFE0, 0xFFFFFFF0, 0xFFFFFFF8, 0xFFFFFFFC, 0xFFFFFFFE,
            0xFFFFFFFF };
    private static long ipToLong(String strIP) {
        long[] ip = new long[4];
        String[] ipSec = strIP.split("\\.");
        for (int k = 0; k < 4; k++) {
            ip[k] = Long.valueOf(ipSec[k]);
        }
        return (ip[0] << 24) + (ip[1] << 16) + (ip[2] << 8) + ip[3];
    }
    private static String longToIP(long longIP) {
        StringBuffer sb = new StringBuffer("");
        sb.append(String.valueOf(longIP >>> 24));
        sb.append(".");
        sb.append(String.valueOf((longIP & 0x00FFFFFF) >>> 16));
        sb.append(".");
        sb.append(String.valueOf((longIP & 0x0000FFFF) >>> 8));
        sb.append(".");
        sb.append(String.valueOf(longIP & 0x000000FF));
        return sb.toString();
    }

}
