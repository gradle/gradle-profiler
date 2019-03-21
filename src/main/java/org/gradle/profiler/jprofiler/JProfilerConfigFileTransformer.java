package org.gradle.profiler.jprofiler;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

public class JProfilerConfigFileTransformer {

    public static File transform(File configFile, String id, JProfilerConfig jProfilerConfig, String snapshotPath, boolean captureOnExit) {
        try {
            File transformedConfigFile = File.createTempFile("jprofiler", ".xml");
            transformedConfigFile.deleteOnExit();
            File probesFile = createProbesDocument(jProfilerConfig);
            URL resource = JProfilerConfigFileTransformer.class.getResource("/jprofiler/transform.xsl");
            Templates template = TransformerFactory.newInstance().newTemplates(new StreamSource(resource.openStream()));
            Source source = new StreamSource(new FileInputStream(configFile));
            Result result = new StreamResult(new FileOutputStream(transformedConfigFile));
            Transformer transformer = template.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setParameter("id", id);
            transformer.setParameter("allocRecording", jProfilerConfig.isRecordAlloc());
            transformer.setParameter("monitorRecording", jProfilerConfig.isRecordMonitors());
            transformer.setParameter("probesFile", probesFile.getPath());
            transformer.setParameter("snapshotPath", snapshotPath);
            transformer.setParameter("captureOnJvmStop", captureOnExit);
            transformer.transform(source, result);
            if (Boolean.getBoolean("jprofiler.debugTransform")) {
                Files.readAllLines(transformedConfigFile.toPath()).forEach(System.out::println);
            }
            return transformedConfigFile;
        } catch (TransformerException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static File createProbesDocument(JProfilerConfig jProfilerConfig) throws ParserConfigurationException, TransformerException, IOException {
        File probesFile = File.createTempFile("jprofiler", ".xml");
        probesFile.deleteOnExit();
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element probesElement = document.createElement("probes");
        for (String probeName : jProfilerConfig.getRecordedProbes()) {
            Element probeElement = document.createElement("probe");
            probeElement.setAttribute("name", probeName);
            probeElement.setAttribute("events", String.valueOf(jProfilerConfig.getProbesWithEventRecording().contains(probeName)));
            probeElement.setAttribute("recordSpecial", String.valueOf(jProfilerConfig.getProbesWithSpecialRecording().contains(probeName)));
            probesElement.appendChild(probeElement);
        }
        document.appendChild(probesElement);
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(new FileOutputStream(probesFile));
        TransformerFactory.newInstance().newTransformer().transform(source, result);
        return probesFile;
    }

}
