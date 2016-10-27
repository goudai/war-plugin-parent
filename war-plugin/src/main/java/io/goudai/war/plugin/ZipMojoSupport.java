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
import java.nio.charset.Charset;
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

    @Parameter(required = false)
    protected String[] includes;

    @Parameter(required = false)
    protected String[] excludeDirectorys;

    @Parameter(required = false)
    private String[] excludeFiles;

    @Parameter(required = true)
    private String accessKey;

    @Parameter(required = true)
    private String password;

    @Parameter(required = true)
    private String prefix;

    @Parameter(required = true)
    private String projectName;

    @Parameter(required = true)
    private String bucketName;

    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    private File sourceDirectory;

    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    private String webapp;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
    private String dest;


    static private MessageDigest instance;
    private final String DEFAULT_ENDPOINT = "http://oss-cn-hangzhou.aliyuncs.com";
    private OSSClient ossClient;
    private List<File> deleteFiles = new ArrayList<File>(50);
    private List<File> jspFiles = new ArrayList<File>(50);
    private HashMap<String, String> mapping = new HashMap<String, String>();
    ExecutorService executorService = Executors.newFixedThreadPool(20);


    public void execute() throws MojoExecutionException, MojoFailureException {
        excludeDirectorys = excludeDirectorys == null ? new String[0] : excludeDirectorys;
        excludeFiles = excludeFiles == null ? new String[0] : excludeFiles;
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
                String fileName = file.getName();
                if (file.isDirectory()) {
                    boolean isExclued = false;
                    for (String exclude : excludeDirectorys) {
                        if (fileName.equals(exclude)) {
                            isExclued = true;
                            break;
                        }
                    }
                    if (!isExclued) {
                        doExecute(file);
                    }
                } else {
                    boolean isExclued = false;
                    for (String exclude : excludeFiles) {
                        if (fileName.equals(exclude)) {
                            isExclued = true;
                            break;
                        }
                    }
                    if (!isExclued) {
                        upload(file);
                    }
                }
            }
        } else {
            boolean isExclued = false;
            for (String exclude : excludeFiles) {
                if (root.getName().equals(exclude)) {
                    isExclued = true;
                    break;
                }
            }
            if (!isExclued) {
                upload(root);
            }

        }

    }

    private void upload(File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".xml") || fileName.equals("robots.txt") || fileName.equals("favicon.ico")) return;

        if (fileName.endsWith(".jsp")) {
            this.jspFiles.add(file);
            return;
        }
        if (!file.getAbsolutePath().endsWith(".jsp")) {
            this.deleteFiles.add(file);
            String name = "${stc}/" + getPath(file);
            if (fileName.endsWith(".js")) {
                handleJs(file, name);
            } else if (fileName.endsWith(".css")) {
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
            ;
            try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),"UTF-8"))) {
                bufferedWriter.write(readJsp2String);
                bufferedWriter.flush();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readJsp2String(File jsp) {
        StringBuilder dest = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsp), "UTF-8"))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                dest.append(line + "\r\n");
            }
            return dest.toString();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private String getPath(File srcFile) {
        return srcFile.getPath().replace(webapp.replace("/", File.separator), "").replace(File.separator, "/").replaceFirst("/", "");
    }

    //"http://static.mayishike.cm/"
    private void handleJs(File file, String name) {
        String content = JSCSSZipHelpr.compress2JS(file);
        String newFileName = file.getName().split("\\.")[0] + ".hash." + parseByte2HexStr(instance.digest(content.getBytes())) + ".js";
        String key = getPath(file).replace(file.getName(), newFileName).replace("\\", "/");

        String value = prefix + "/" + projectName + "/" + key;
        mapping.put(name, value);

        upload(file.getName(), content, key);
    }

    private void handleCss(File file, String name) {
        if(name.endsWith("components.css")) {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            String mimetype = Mimetypes.getInstance().getMimetype(file.getName());
            if (mimetype != null) {
                objectMetadata.setContentType(mimetype);
            }
            String key = getPath(file);
            mapping.put(name, prefix + "/" + projectName + "/" + key);
            getLog().info(file.getName() + " ----->                                      " + prefix + "/" + projectName + "/" + key  + "                     " + mimetype);
            objectMetadata.setContentLength(file.length());
            ossClient.putObject(bucketName, projectName + "/" + key, file, objectMetadata);

        }else{
            String content = JSCSSZipHelpr.compress2CSS(file);
            String newFileName = file.getName().split("\\.")[0] + ".hash." + parseByte2HexStr(instance.digest(content.getBytes())) + ".css";
            String key = getPath(file).replace(file.getName(), newFileName).replace("\\", "/");

            mapping.put(name, prefix + "/" + projectName + "/" + key);

            upload(file.getName(), content, key);

        }

    }

    private void handleImage(File file, String name) throws IOException {
        String newFileName = file.getName();
        String key = getPath(file).replace("\\", "/");

        String value = prefix + "/" + projectName + "/" + key;
        mapping.put(name, value);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        String mimetype = Mimetypes.getInstance().getMimetype(file.getName());
        if (mimetype != null) {
            objectMetadata.setContentType(mimetype);
        }
        getLog().info(file.getName() + " ----->                                      " + value + "                     " + mimetype);
        try {
            byte[] buf = IOUtils.readStreamAsByteArray(new FileInputStream(file));
            objectMetadata.setContentLength(buf.length);
            ossClient.putObject(bucketName, projectName + "/" + key, new ByteArrayInputStream(buf), objectMetadata);
        } catch (OSSException e) {
            getLog().error(e.getMessage(), e);
        } catch (ClientException e) {
            getLog().error(e.getMessage(), e);
        }
    }

    private void upload(String filename, String content, String key) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        String mimetype = Mimetypes.getInstance().getMimetype(filename);
        key = projectName + "/" + key;
        if (mimetype != null) {
            objectMetadata.setContentType(mimetype);
        }
        getLog().info(filename + " ---->                                     " + prefix + "/" + key + "                     " + mimetype);
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