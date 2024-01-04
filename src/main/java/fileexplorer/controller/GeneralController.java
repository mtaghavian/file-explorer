package fileexplorer.controller;

import fileexplorer.Application;
import fileexplorer.config.HttpInterceptor;
import fileexplorer.misc.StreamUtils;
import fileexplorer.misc.SystemUtils;
import fileexplorer.model.dto.SystemFile;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.util.*;

@RestController
@RequestMapping("/api/general")
public class GeneralController {

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private HttpInterceptor httpInterceptor;

    public MediaType getMediaType(String fileName) {
        try {
            String mimeType = servletContext.getMimeType(fileName);
            MediaType mediaType = MediaType.parseMediaType(mimeType);
            return mediaType;
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }


    @GetMapping("/systemInfo")
    public void system(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder sb = new StringBuilder();
        Runtime runtime = Runtime.getRuntime();
        String lineBreak = "\n";
        String separator = "=";
        sb.append("availableProcessors" + separator + runtime.availableProcessors()).append(lineBreak);
        sb.append("freeMemory" + separator + runtime.freeMemory()).append(lineBreak);
        sb.append("maxMemory" + separator + runtime.maxMemory()).append(lineBreak);
        sb.append("totalMemory" + separator + runtime.totalMemory()).append(lineBreak);
        File file = new File(".");
        sb.append("getFreeSpace" + separator + file.getFreeSpace()).append(lineBreak);
        sb.append("getUsableSpace" + separator + file.getUsableSpace()).append(lineBreak);
        sb.append("getTotalSpace" + separator + file.getTotalSpace()).append(lineBreak);
        for (Object prop : System.getProperties().keySet()) {
            sb.append(prop + separator + System.getProperty(prop.toString())).append(lineBreak);
        }
        StreamUtils.responseOnHtml(response, sb.toString(), false);
    }

    @GetMapping("/clientIP")
    public void clientIP(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        StreamUtils.responseOnHtml(resp, StreamUtils.getUserAddressDesc(req), false);
    }

    @GetMapping("/serverIP")
    public void serverIP(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder();
        String lineBreak = "\n";
        ArrayList<NetworkInterface> list = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (int i = 0; i < list.size(); i++) {
            NetworkInterface adapter = list.get(i);
            sb.append("Interface #" + i + lineBreak);
            sb.append("Display name: " + adapter.getDisplayName() + lineBreak);
            sb.append("Name: " + adapter.getName() + lineBreak);
            sb.append("Index: " + adapter.getIndex() + lineBreak);
            Enumeration<InetAddress> inetAddresses = adapter.getInetAddresses();
            ArrayList<InetAddress> addressList = Collections.list(inetAddresses);
            for (int j = 0; j < addressList.size(); j++) {
                sb.append("Address[" + j + "] " + addressList.get(j).getHostName() + "=" + addressList.get(j).getHostAddress() + lineBreak);
            }
            sb.append(lineBreak);
        }
        StreamUtils.responseOnHtml(resp, sb.toString(), false);
    }

    @GetMapping("/shutdown")
    public void shutdown(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Application.shutdown();
        StreamUtils.responseOnHtml(response, "Service is shutting down...", true);
    }

    @GetMapping("/restart")
    public void restart(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Application.restart();
        StreamUtils.responseOnHtml(response, "Service is restarting...", true);
    }

    @PostMapping("/execute")
    public String execute(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr, @RequestBody(required = false) String cmd) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        if (cmd == null) {
            cmd = "";
        }
        return SystemUtils.executeSingleCommand(addr, cmd);
    }

    @GetMapping("/video/stream")
    public Mono<ResponseEntity<byte[]>> streamVideo(HttpServletRequest req, HttpServletResponse resp, @RequestHeader(value = "Range", required = false) String range, @RequestParam String addr) {
        try {
            addr = addr.trim().replace("\u00a0", " ");

            int chunkSize = 1000000;
            File file = new File(addr);
            if (!file.exists()) {
                return null;
            }
            long start = 0;
            long end = chunkSize;
            long size = file.length();
            if (range != null) {
                String[] ranges = range.split("-");
                start = Long.parseLong(ranges[0].substring(6));
                end = (ranges.length > 1) ? Long.parseLong(ranges[1]) : (start + chunkSize);
            }
            end = Math.min(end, size - 1);

            byte[] data = StreamUtils.readBytes(file.getPath(), start, end);
            String contentLength = "" + data.length;
            HttpStatus httpStatus = (end >= size) ? httpStatus = HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
            return Mono.just(ResponseEntity.status(httpStatus).header("Content-Type", "" + httpInterceptor.getMediaType(file.getName())).header("Accept-Ranges", "bytes").header("Content-Length", contentLength).header("Content-Range", "bytes " + start + "-" + end + "/" + size).body(data));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @GetMapping("/files/open")
    public void open(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr, @RequestParam(required = false) String arg1, @RequestParam(required = false) String arg2) throws Exception {
        addr = addr.trim().replace("\u00a0", " ");

        File file = new File(addr);
        if (!file.exists()) {
            response.setContentType("text/plain");
            response.getOutputStream().write(("File not found!").getBytes());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        InputStream inputStream = new FileInputStream(file);
        if (addr.toLowerCase().endsWith(".pdf")) {
            PDDocument document = PDDocument.load(inputStream);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int page = Math.min(document.getNumberOfPages() - 1, Integer.parseInt(arg1));
            int dpi = Integer.parseInt(arg2);
            BufferedImage img = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);
            document.close();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", os);
            response.setContentType("image/jpg");
            response.getOutputStream().write(os.toByteArray());
        } else {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            StreamUtils.copy(inputStream, os, true, true);
            response.setContentType("" + getMediaType(file.getName()));
            response.getOutputStream().write(os.toByteArray());
        }
        inputStream.close();
        response.setHeader("Access-Control-Max-Age", "3000");
        response.setHeader("Cache-Control", "max-age=" + "3000");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @GetMapping(value = "/files/download")
    public Mono<ResponseEntity<InputStreamResource>> download(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr, @RequestHeader(value = "Range", required = false) String range) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");

        final File originalFile = new File(addr);
        File sendFile = null;
        String zipFileName = originalFile.getName() + ".zip";
        try {
            if (!originalFile.exists()) {
                throw new RuntimeException("File not found! Name = " + addr);
            }
            if (originalFile.isDirectory()) {
                sendFile = new File(zipFileName);
                ZipUtil.pack(originalFile, sendFile);
            } else {
                sendFile = originalFile;
            }

            long start = 0;
            long size = sendFile.length();
            if (range != null) {
                String[] ranges = range.split("-");
                start = Long.parseLong(ranges[0].substring(6));
            }

            FileInputStream fileInputStream = new FileInputStream(sendFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    if (originalFile.isDirectory()) {
                        new File(zipFileName).delete();
                    }
                }
            };
            fileInputStream.skip(start);
            String contentLength = "" + (size - start);

            HttpStatus httpStatus = (range == null) ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
            return Mono.just(ResponseEntity.status(httpStatus).header("Content-Type", "" + httpInterceptor.getMediaType(sendFile.getName())).header("Content-Disposition", "attachment; " + "filename=\"" + sendFile.getName() + "\"; " + "filename*=UTF-8''" + URLEncoder.encode(sendFile.getName(), "UTF-8").replace("+", "%20")).header("Accept-Ranges", "bytes").header("Content-Length", contentLength).header("Content-Range", "bytes " + start + "-" + (size - 1) + "/" + contentLength).body(new InputStreamResource(fileInputStream)));
        } catch (Exception ioex) {
            throw new RuntimeException("Exception while reading file: " + addr);
        }
    }

    @PostMapping(value = "/files/upload")
    @ResponseBody
    public void uploadFile(HttpServletRequest request, HttpServletResponse response, @RequestParam(required = false, defaultValue = "false") String optimize, @RequestParam String addr, @RequestParam("file") MultipartFile[] files) throws IOException {
        for (MultipartFile mfile : files) {
            String filePath = addr + "/" + mfile.getOriginalFilename();
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
            if (mfile.getSize() == 0) {
                FileOutputStream fos = new FileOutputStream(file);
                fos.close();
            } else {
                File store = ((DiskFileItem) ((CommonsMultipartFile) mfile).getFileItem()).getStoreLocation();
                store.renameTo(file);
            }
            try {
                if (optimize.equals("true")) {
                    String fname = file.getName().trim().toLowerCase();
                    if (fname.endsWith(".jpg") || fname.endsWith(".jpeg")) {
                        optimize(request, response, file.getPath());
                    }
                }
            } catch (Exception e) {
                file.delete();
            }
        }
    }

    @GetMapping(value = "/files/delete")
    public void delete(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        try {
            File file = new File(addr);
            if (!file.exists()) {
                throw new RuntimeException("File not found! Name = " + addr);
            }
            StreamUtils.delete(file);
        } catch (Exception ioex) {
            throw new RuntimeException("Exception while deleting file: " + addr);
        }
    }

    @GetMapping(value = "/files/createfile")
    public void createfile(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        try {
            File file = new File(addr);
            FileOutputStream fos = new FileOutputStream(file);
            fos.close();
        } catch (Exception ioex) {
            throw new RuntimeException("Exception while deleting file: " + addr);
        }
    }

    @GetMapping(value = "/files/createfolder")
    public void createfolder(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        try {
            File file = new File(addr);
            file.mkdir();
        } catch (Exception ioex) {
            throw new RuntimeException("Exception while deleting file: " + addr);
        }
    }

    @GetMapping(value = "/files/rename")
    public void rename(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr, @RequestParam String naddr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        naddr = naddr.trim().replace("\u00a0", " ");
        File file = new File(addr);
        if (!file.exists()) {
            throw new RuntimeException("File not found! Name = " + addr);
        }
        file.renameTo(new File(naddr));
    }

    @GetMapping(value = "/files/detail")
    public SystemFile detail(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        File file = new File(addr);
        if (!file.exists()) {
            throw new RuntimeException("File not found! Name = " + addr);
        }
        SystemFile f = new SystemFile();
        f.setName(file.getName());
        f.setLength(file.length());
        f.setIsDirectory(file.isDirectory());
        f.setMdate(file.lastModified());
        return f;
    }

    @GetMapping(value = "/files/rotate")
    public void rotate(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        File file = new File(addr);
        if (!file.exists()) {
            throw new RuntimeException("File not found! Name = " + addr);
        }
        String fname = file.getName().trim().toLowerCase();
        if (fname.endsWith(".jpg") || fname.endsWith(".jpeg")) {
            File tempFile = new File("" + System.currentTimeMillis() + "-" + fname);
            String out = SystemUtils.executeInBash(null, "ffmpeg -loglevel error -threads 1 -y -i \"" + addr + "\" -vf transpose=1 \"" + tempFile.getPath() + "\"");
            if (out != null) {
                file.delete();
                tempFile.renameTo(file);
            }
        }
    }

    @GetMapping(value = "/files/optimize")
    public void optimize(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        File file = new File(addr);
        if (!file.exists()) {
            throw new RuntimeException("File not found! Name = " + addr);
        }
        String fname = file.getName().trim().toLowerCase();
        File tempFile = new File("" + System.currentTimeMillis() + "-" + fname);
        String out = SystemUtils.executeInBash(null, "ffmpeg -loglevel error -threads 1 -y -i \"" + addr + "\" \"" + tempFile.getPath() + "\"");
        if (out != null) {
            file.delete();
            tempFile.renameTo(file);
        }
    }

    @GetMapping("/files/list")
    public List<SystemFile> list(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        List<SystemFile> list = new ArrayList<>();
        File dir = new File(addr);
        if (!dir.isDirectory()) {
            return list;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return list;
        }
        for (File file : files) {
            SystemFile ent = new SystemFile();
            ent.setName(file.getName());
            ent.setIsDirectory(file.isDirectory());
            ent.setLength(file.length());
            ent.setMdate(file.lastModified());
            list.add(ent);
        }
        Collections.sort(list, Comparator.comparing(SystemFile::getIsDirectory).reversed().thenComparing(SystemFile::getName));
        return list;
    }

}
