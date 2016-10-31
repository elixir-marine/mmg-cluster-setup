package no.uit.metapipe.cpouta;

import com.jcraft.jsch.*;
import com.jcraft.jsch.KeyPair;
import org.jclouds.openstack.nova.v2_0.domain.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;

/**
 * Created by alexander on 8/26/16.
 */
final class Utils
{

    private Utils() { }

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
        System.out.println("Starting SSH session to... " + userName + "@" + ip);
        try {
            session = ssh.getSession(userName, ip);
            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.connect();
            ChannelExec channel = (ChannelExec)session.openChannel("exec");
            BufferedReader in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            channel.setCommand(aBigCommandString);
            channel.connect();
            System.out.println("Executing commands... ");
            String msg;
            while((msg = in.readLine()) != null)
            {
                System.out.println(msg);
            }
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
            String[] folders = dest.split("/");
            Properties props = new Properties();
            session = ssh.getSession(userName, ip);
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.connect();
            ChannelSftp channel = (ChannelSftp)session.openChannel("sftp");
            channel.connect();
            System.out.println("Copying files... ");
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
    static void updateFileValue(String filePath, String valueName, String newValue)
    {
        try
        {
            List<String> lines;
            List<String> newLines = new ArrayList<String>();
            File f = new File(filePath);
            lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
            for(String line: lines)
            {
                if(line.contains(valueName))
                {
                    if(newValue != null)
                    {
                        newLines.add(updateFileLine(line, valueName, newValue));
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
            System.out.println("Config file updated: '" + valueName + "' changed to '" + newValue + "'.");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    static String updateFileLine(String line, String valueName, String newValue)
    {
        return line.substring(0, line.indexOf(valueName)) + valueName + ": " + String.valueOf(newValue);
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
        else if(object instanceof Integer)
        {
            return object != null && ((Integer) object) != 0;
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
            //throw new IllegalStateException(errorMessage);
            System.out.println("\n" + errorMessage + "\n");
            exit(1);
        }
        return object;
    }

    static String stringValidate(String object, String errorMessage)
    {
        return (String)objectValidate(object, errorMessage);
    }

    static Integer nonZeroIntValidate(Integer object, String errorMessage)
    {
        return (Integer)objectValidate(object, errorMessage);
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

}
