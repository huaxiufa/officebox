package com.officebox.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class OfficeConversionService {
    public Path convertToPdf(Path input, Path outputDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("libreoffice", "--headless", "--convert-to", "pdf", "--outdir", outputDir.toString(), input.toString())
                .redirectErrorStream(true).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("LibreOffice conversion timed out");
        }
        if (process.exitValue() != 0) throw new IOException("LibreOffice conversion failed");
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Path pdf = outputDir.resolve(base + ".pdf");
        if (!Files.exists(pdf)) throw new IOException("Converted PDF was not created");
        return pdf;
    }
}
