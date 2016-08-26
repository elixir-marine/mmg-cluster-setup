package no.uit.metapipe.cpouta;

import com.jcraft.jsch.*;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Server;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import static java.lang.System.exit;
import static java.lang.Thread.sleep;

/**
 * Created by alexander on 8/26/16.
 */
final class Utils
{

    private Utils() { }

    static void localExecutor(String commands)
    {
        Process p;
        try
        {
            p = new ProcessBuilder("bash", "-c", commands)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine())!= null)
            {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        } catch (JSchException e) {
            e.printStackTrace();
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            if(errors.toString().contains("Connection refused") || errors.toString().contains("No route to host"))
            {
                try {
                    sleep(1000);
                } catch (InterruptedException e1) {
                    e.printStackTrace();
                }
                sshExecutor(ssh, userName, ip, aBigCommandString);
            }
            else
            {
                exit(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }
    }

    static void sshCopier(JSch ssh, String userName, String ip, String[] src, String dest)
    {
        Session session;
        System.out.println("Starting SSH session to... " + userName + "@" + ip);
        try {
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
            channel.cd(channel.getHome());
            for (String folder : folders)
            {
                if (folder.length() > 0)
                {
                    try {
                        channel.cd(folder);
                    }
                    catch ( SftpException e ) {
                        channel.mkdir(folder);
                        channel.cd(folder);
                    }
                }
            }
            channel.cd(channel.getHome());
            for(String s : src)
            {
                f = new File(s);
                if(!f.isDirectory())
                {
                    System.out.println("Copying file: " + s);
                    channel.put(s, channel.getHome() + dest, ChannelSftp.OVERWRITE);
                }
                else
                {
                    tempDir = channel.getHome() + dest + "/" + f.getName();
                    try {
                        channel.mkdir(tempDir);
                    } catch (Exception e) {
                        System.out.println(tempDir + ": old directory exists! Starting second SSH channel for recursive deletion...");
                        commands = "rm -rv " + tempDir + ";";
                        sshExecutor(ssh, userName, ip, commands);
                        System.out.println("Recursive deletion complete.");
                        channel.mkdir(channel.getHome() + dest + "/" + f.getName());
                    }
                    sshCopierFolderHelper(channel, f, tempDir);
                }
            }
            channel.disconnect();
            session.disconnect();
            System.out.println("SSH session over.");
        } catch (JSchException e) {
            e.printStackTrace();
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            if(errors.toString().contains("Connection refused") || errors.toString().contains("No route to host"))
            {
                try {
                    sleep(1000);
                } catch (InterruptedException e1) {
                    e.printStackTrace();
                }
                sshCopier(ssh, userName, ip, src, dest);
            }
            else
            {
                exit(1);
            }
        } catch (SftpException e) {
            e.printStackTrace();
            exit(1);
        }
    }

    private static void sshCopierFolderHelper(ChannelSftp channel, File f, String dest) throws SftpException
    {
        ArrayList<File> list = new ArrayList<File>(Arrays.asList(f.listFiles()));
        for (File item : list)
        {

            if (!item.isDirectory())
            {
                System.out.println("Copying file: " + item.getAbsolutePath());
                channel.put(item.getAbsolutePath(), dest, ChannelSftp.OVERWRITE);
            }
            else if (!".".equals(f.getName()) || "..".equals(f.getName()))
            {
                System.out.println("Copying directory: " + dest + "/" + item.getName());
                channel.mkdir(dest + "/" + item.getName());
                sshCopierFolderHelper(channel, item, dest + "/" + item.getName()); // Enter found folder on server to read its contents and create locally.
            }

        }
    }

    static String getFileNameFromPath(String path)
    {
        File f = new File(path);
        return f.getName();
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

}
