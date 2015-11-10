package br.eti.mertz.wkhtmltopdf.wrapper;

import br.eti.mertz.wkhtmltopdf.wrapper.configurations.WrapperConfig;
import br.eti.mertz.wkhtmltopdf.wrapper.configurations.WrapperConfigBuilder;
import br.eti.mertz.wkhtmltopdf.wrapper.page.Page;
import br.eti.mertz.wkhtmltopdf.wrapper.page.PageType;
import br.eti.mertz.wkhtmltopdf.wrapper.params.Param;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Pdf implements PdfService {

    private static final String STDINOUT = "-";

    private WrapperConfig wrapperConfig;

    private List<Param> params;

    private List<Page> pages;

    private boolean hasToc = false;

    public Pdf(WrapperConfig wrapperConfig) {
        this.wrapperConfig = wrapperConfig;
        this.params = new ArrayList<Param>();
        this.pages = new ArrayList<Page>();
    }

    public Pdf() {
        this(new WrapperConfigBuilder().build());
    }

    public void addPage(String source, PageType type) {
        this.pages.add(new Page(source, type));
    }

    public void addToc() {
        this.hasToc = true;
    }

    public void addParam(Param param) {
        params.add(param);
    }

    public void addParam(Param... params) {
        for (Param param : params) {
            addParam(param);
        }
    }

    public void saveAs(String path) throws IOException, InterruptedException {
        saveAs(path, getPDF());
    }

    private File saveAs(String path, byte[] document) throws IOException {
        File file = new File(path);

        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        bufferedOutputStream.write(document);
        bufferedOutputStream.flush();
        bufferedOutputStream.close();

        return file;
    }

    public byte[] getPDF() throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(getCommandAsArray());

        for (Page page : pages) {
            if (page.getType().equals(PageType.htmlAsString)) {
                OutputStream stdInStream = process.getOutputStream();
                stdInStream.write(page.getSource().getBytes("UTF-8"));
                stdInStream.close();
            }
        }

        StreamEater outputStreamEater = new StreamEater(process.getInputStream());
        outputStreamEater.start();

        StreamEater errorStreamEater = new StreamEater(process.getErrorStream());
        errorStreamEater.start();

        outputStreamEater.join();
        errorStreamEater.join();
        process.waitFor();

        if (process.exitValue() != 0) {
            throw new RuntimeException("Process (" + getCommand() + ") exited with status code " + process.exitValue() + ":\n" + new String(errorStreamEater.getBytes()));
        }

        if (outputStreamEater.getError() != null) {
        	throw outputStreamEater.getError();
        }

        if (errorStreamEater.getError() != null) {
        	throw errorStreamEater.getError();
        }

        return outputStreamEater.getBytes();
    }

    private String[] getCommandAsArray() {
        List<String> commandLine = new ArrayList<String>();
        commandLine.add(wrapperConfig.getWkhtmltopdfCommand());

        if (hasToc)
            commandLine.add("toc");

        for (Param p : params) {
            commandLine.add(p.getKey());

            String value = p.getValue();

            if (value != null) {
                commandLine.add(p.getValue());
            }
        }

        for (Page page : pages) {
            if (page.getType().equals(PageType.htmlAsString)) {
                commandLine.add(STDINOUT);
            } else {
                commandLine.add(page.getSource());
            }
        }
        commandLine.add(STDINOUT);
        return commandLine.toArray(new String[commandLine.size()]);
    }

    public String getCommand() {
        return StringUtils.join(getCommandAsArray(), " ");
    }

    private class StreamEater extends Thread {

    	private InputStream stream;
		private ByteArrayOutputStream bytes;

		private IOException error;

		public StreamEater(InputStream stream) {
			this.stream = stream;

	        bytes = new ByteArrayOutputStream();
		}

		public void run() {
			try {
				int bytesRead = stream.read();
				while (bytesRead >= 0) {
					bytes.write(bytesRead);
					bytesRead = stream.read();
				}

				stream.close();
			} catch (IOException e) {
				e.printStackTrace();

				error = e;
			}
		}

		public IOException getError() {
			return error;
		}

		public byte[] getBytes() {
			return bytes.toByteArray();
		}
    }
}
