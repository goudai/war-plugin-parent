package io.goudai.war.plugin;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.utils.IOUtils;
import com.aliyun.oss.internal.Mimetypes;
import com.aliyun.oss.model.ObjectMetadata;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
@Mojo(name = "zip")
public class ZipMojoSupport extends AbstractMojo {

    @Parameter
    protected String[] includes;

    @Parameter
    protected String[] excludes;

    @Parameter
    private String accessKey;

    @Parameter
    private String password;

    @Parameter
    private String prefix;

    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    private File sourceDirectory;
    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    private String webapp;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
    private String dest;

    static private MessageDigest instance;


    private final String DEFAULT_ENDPOINT = "http://oss-cn-hangzhou.aliyuncs.com";
    OSSClient ossClient;
    String bucketName = "sk-static";


    private List<File> deleteFiles = new ArrayList<File>(50);
    private List<File> jspFiles = new ArrayList<File>(50);
    private HashMap<String, String> mapping = new HashMap<String, String>();
    ExecutorService executorService = Executors.newFixedThreadPool(20);


    public void execute() throws MojoExecutionException, MojoFailureException {
        ossClient = new OSSClient(DEFAULT_ENDPOINT, accessKey, password);
        try {
            instance = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        doExecute(sourceDirectory);
        repJsp();

        executorService.shutdown();

    }

    void doExecute(File root) {
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    doExecute(file);
                } else {
                    upload(file);
                }
            }
        } else {
            upload(root);
        }

    }

    private void upload(File file) {
        if(file.getName().endsWith(".xml"))return;
        if (file.getName().endsWith(".jsp")) {
            this.jspFiles.add(file);
            return;
        }
        if (!file.getAbsolutePath().endsWith(".jsp")) {
            this.deleteFiles.add(file);
            String name = "${ctx}/" + getPath(file);
            if (file.getName().endsWith(".js")) {
                handleJs(file, name);
            } else if (file.getName().endsWith(".css")) {
                handleCss(file, name);
            } else {
                try {
                    handleImage(file, name);
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
    }


    private void repJsp() {
        dest = dest.replace("\\", File.separator).replace("/", File.separator);
        String replace = webapp.replace("/", File.separator);
        for (File jsp : this.jspFiles) {
            if (jsp.exists()) {
                String readJsp2String = this.readJsp2String(jsp);
                for (Map.Entry<String, String> entry : this.mapping.entrySet()) {
                    readJsp2String = readJsp2String.replaceAll(entry.getKey().replace("$", "\\$")
                            .replace("{", "\\{")
                            .replace("}", "\\}")
                            .replaceAll("/", "\\/"), entry.getValue());
                }
                String savePath = jsp.getPath().replace(replace, dest);
                write2File(savePath, readJsp2String);
            }
        }
        File file2 = new File(dest + ".war");
        if (file2.isFile()) {
            file2.delete();
        }
        for (File file : deleteFiles) {
            String replace2 = file.getPath().replace(replace, dest);
            File file3 = new File(replace2);
            if (file3.isFile()) {
                file3.delete();
            }
        }

        ZipUtils.createZip(dest, dest + ".war");
        getLog().info("重新生成war成功" + dest + ".war");

    }

    private void write2File(String savePath, String readJsp2String) {
        try {
            File file = new File(savePath);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            try (BufferedWriter bf = new BufferedWriter(new FileWriter(file))) {
                bf.write(readJsp2String);
                bf.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readJsp2String(File jsp) {
        StringBuilder dest = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(jsp))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                dest.append(line+"\r\n");
            }
            return dest.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private String getPath(File srcFile) {
        return srcFile.getPath().replace(webapp.replace("/", File.separator), "").replace(File.separator, "/").replaceFirst("/", "");
    }

    //"http://static.mayishike.cm/"
    private void handleJs(File file, String name) {
        String content = JSCSSZipHelpr.compress2JS(file);
        String newFileName = file.getName().split("\\.")[0] +".hash." +parseByte2HexStr(instance.digest(content.getBytes())) + ".js";
        String key = getPath(file).replace(file.getName(), newFileName).replace("\\", "/");

        mapping.put(name, prefix + "/" + key);

        upload(file.getName(), content, key);
    }

    private void handleCss(File file, String name) {
        String content = JSCSSZipHelpr.compress2CSS(file);
        String newFileName = file.getName().split("\\.")[0] +".hash." + parseByte2HexStr(instance.digest(content.getBytes())) + ".css";
        String key = getPath(file).replace(file.getName(), newFileName).replace("\\", "/");

        mapping.put(name, prefix + "/" + key);

        upload(file.getName(), content, key);

    }

    private void handleImage(File file, String name) throws IOException {
        String newFileName = file.getName().split("\\.")[0] +".hash." +  parseByte2HexStr(instance.digest(FileUtils.fileRead(file).getBytes())) + "."+file.getName().split("\\.")[1];
        String key = getPath(file).replace(file.getName(), newFileName).replace("\\", "/");

        mapping.put(name, prefix + "/" + key);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        String mimetype = Mimetypes.getInstance().getMimetype(file.getName());
        if (mimetype != null) {
            objectMetadata.setContentType(mimetype);
        }
        getLog().info(file.getName() +" ----->                                      http://static.mayishike.com/" + key + "                     " + mimetype);
        try {
            byte[] buf = IOUtils.readStreamAsByteArray(new FileInputStream(file));
            objectMetadata.setContentLength(buf.length);
            ossClient.putObject(bucketName, key, new ByteArrayInputStream(buf), objectMetadata);
        } catch (OSSException e) {
            getLog().error(e.getMessage(), e);
        } catch (ClientException e) {
            getLog().error(e.getMessage(), e);
        }
    }

    private void upload(String filename, String content, String key) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        String mimetype = Mimetypes.getInstance().getMimetype(filename);
        if (mimetype != null) {
            objectMetadata.setContentType(mimetype);
        }
        getLog().info(filename + " ---->                                      http://static.mayishike.com/" + key + "                     " + mimetype);
        try {
            byte[] buf = content.getBytes();
            objectMetadata.setContentLength(buf.length);
            ossClient.putObject(bucketName, key, new ByteArrayInputStream(buf), objectMetadata);
        } catch (OSSException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (ClientException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    public static String parseByte2HexStr(byte buf[]) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buf.length; i++) {
            String hex = Integer.toHexString(buf[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toLowerCase());
        }
        return sb.toString();
    }

    public static void main(String[] args) {
//        String str = "<script type=\"javascript\" src=\"${ctx}/js/area.js\"/>";
//        String $ = "${ctx}/js/area.js".replace("$", "\\$")
//                .replace("{", "\\{")
//                .replace("}", "\\}")
//                .replaceAll("/", "\\\\/");

//        System.out.println(str.replaceAll($,"http://static.mayishike.con/js/*.js"));
    }
}