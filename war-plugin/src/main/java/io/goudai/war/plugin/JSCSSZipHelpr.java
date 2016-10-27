package io.goudai.war.plugin;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import java.io.*;


public class JSCSSZipHelpr {

    static File dir = new File("/Users/freeman/IdeaProjects/miziProjects/sk/sk-static/js");
//	static File out = new  File("/Users/freeman/IdeaProjects/miziProjects/sk/sk-static/js/dest");

    static int linebreakpos = -1;
    static boolean munge = true;
    static boolean verbose = false;
    static boolean preserveAllSemiColons = true;
    static boolean disableOptimizations = false;

    public static void main(String[] args) throws Exception {
        listFiles(dir);
    }

    public static void listFiles(File file) throws Exception {
        if (file.isDirectory() && !file.getName().startsWith("_")) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                listFiles(files[i]);
            }
        } else if (file.isFile()) {
            if (!file.getName().startsWith(".")
                    && file.getName().endsWith(".js")
                    || file.getName().endsWith(".css")) {
                System.out.println(compress2JS(file));
            }
        }
    }


    public static String compress2JS(final File srcFile) {
        try {
            Reader in = new InputStreamReader(new FileInputStream(srcFile),
                    "utf-8");
            Writer out = new StringWriter();
            String fileName = srcFile.getName();
            if (fileName.endsWith(".js")) {
                ErrorReporter reporter = create(srcFile);
                JavaScriptCompressor jscompressor = new JavaScriptCompressor(in, reporter);
                jscompressor.compress(out, linebreakpos, munge, verbose,
                        preserveAllSemiColons, disableOptimizations);
            }
            in.close();
            return out.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String compress2CSS(final File srcFile) {
        try {
            Reader in = new InputStreamReader(new FileInputStream(srcFile),
                    "utf-8");
            Writer out = new StringWriter();
            String fileName = srcFile.getName();
            if (fileName.endsWith(".css")) {
                if (fileName.equals("components.css")) {
                    try (BufferedReader reader = new BufferedReader(in)) {
                        String line = null;
                        while ((line = reader.readLine()) != null) {
                            out.append(line + "\r\n");
                        }
                        return out.toString();

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    CssCompressor csscompressor = new CssCompressor(in);
                    csscompressor.compress(out, linebreakpos);
                }
            }
            in.close();
            return out.toString().replace("?","✔");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ErrorReporter create(final File srcFile) {
        ErrorReporter reporter = new ErrorReporter() {
            public void warning(String message, String sourceName, int line,
                                String lineSource, int lineOffset) {
                if (line < 0) {
                    System.err.println("\n[WARNING] " + message);
                } else {
                    System.err.println("\n[WARNING] " + line + ':' + lineOffset
                            + ':' + message);
                }
            }

            public void error(String message, String sourceName, int line,
                              String lineSource, int lineOffset) {
                System.out.println("---解析文件出错:" + srcFile.getAbsoluteFile());
            }

            public EvaluatorException runtimeError(String message,
                                                   String sourceName, int line, String lineSource,
                                                   int lineOffset) {
                error(message, sourceName, line, lineSource, lineOffset);
                return new EvaluatorException(message);
            }
        };
        return reporter;
    }
}