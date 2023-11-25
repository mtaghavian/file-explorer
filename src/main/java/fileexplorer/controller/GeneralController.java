package fileexplorer.controller;

import fileexplorer.Application;
import fileexplorer.config.HttpInterceptor;
import fileexplorer.misc.ImageUtils;
import fileexplorer.misc.StreamUtils;
import fileexplorer.misc.SystemUtils;
import fileexplorer.model.dto.SystemFile;
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
import org.zeroturnaround.zip.ZipUtil;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
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
    public String execute(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr, @RequestBody String cmd) {
        addr = addr.trim().replace("\u00a0", " ");
        return SystemUtils.execute(addr, cmd);
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
            return Mono.just(ResponseEntity.status(httpStatus)
                    .header("Content-Type", "" + httpInterceptor.getMediaType(file.getName()))
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Length", contentLength)
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + size)
                    .body(data));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @GetMapping("/files/open")
    public void open(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws Exception {
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
            List<BufferedImage> images = new ArrayList<>();
            for (int i = 0; i < Math.min(document.getNumberOfPages(), 10); i++) {
                images.add(pdfRenderer.renderImageWithDPI(i, 350, ImageType.RGB));
            }
            document.close();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(ImageUtils.merge(images, true, true), "jpg", os);
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
        try {
            if (!originalFile.exists()) {
                throw new RuntimeException("File not found! Name = " + addr);
            }
            if (originalFile.isDirectory()) {
                sendFile = new File(originalFile.getName() + ".zip");
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

            FileInputStream fileInputStream = new FileInputStream(sendFile);
            fileInputStream.skip(start);
            String contentLength = "" + (size - start);

            HttpStatus httpStatus = (range == null) ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
            return Mono.just(ResponseEntity.status(httpStatus)
                    .header("Content-Type", "" + httpInterceptor.getMediaType(sendFile.getName()))
                    .header("Content-Disposition", "attachment; "
                            + "filename=\"" + sendFile.getName() + "\"; "
                            + "filename*=UTF-8''" + URLEncoder.encode(sendFile.getName(), "UTF-8").replace("+", "%20"))
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Length", contentLength)
                    .header("Content-Range", "bytes " + start + "-" + (size - 1) + "/" + contentLength)
                    .body(new InputStreamResource(fileInputStream)));
        } catch (Exception ioex) {
            throw new RuntimeException("Exception while reading file: " + addr);
        }
    }

    @PostMapping(value = "/files/upload")
    @ResponseBody
    public void uploadFile(HttpServletRequest request, HttpServletResponse response, @RequestParam(required = false, defaultValue = "false") String optimize, @RequestParam String addr, @RequestParam("file") MultipartFile[] files) throws IOException {
        for (MultipartFile file : files) {
            File fileObj = new File(addr + "/" + file.getOriginalFilename());
            try {
                FileOutputStream os = new FileOutputStream(fileObj);
                StreamUtils.copy(file.getInputStream(), os, false, true);

                if (optimize.equals("true")) {
                    String fname = fileObj.getName().trim().toLowerCase();
                    if (fname.endsWith(".jpg") || fname.endsWith(".jpeg")) {
                        optimize(request, response, fileObj.getPath());
                    }
                }
            } catch (Exception e) {
                fileObj.delete();
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
    public String detail(HttpServletRequest request, HttpServletResponse response, @RequestParam String addr) throws IOException {
        addr = addr.trim().replace("\u00a0", " ");
        File file = new File(addr);
        if (!file.exists()) {
            throw new RuntimeException("File not found! Name = " + addr);
        }
        return "Name: " + file.getName() + "<br/>Size: " + StreamUtils.toHumanReadableSIPrefixes(file.length()) + "<br/>MDate: " + new Date(file.lastModified()).toString();
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
            String out = SystemUtils.execute(null, "ffmpeg -loglevel error -threads 1 -y -i \"" + addr + "\" -vf transpose=1 \"" + tempFile.getPath() + "\"");
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
        String out = SystemUtils.execute(null, "ffmpeg -loglevel error -threads 1 -y -i \"" + addr + "\" \"" + tempFile.getPath() + "\"");
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
            list.add(ent);
        }
        Collections.sort(list, Comparator.comparing(SystemFile::getIsDirectory).reversed().thenComparing(SystemFile::getName));
        return list;
    }
}