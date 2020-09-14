package org.gradle.profiler.studio;

import org.gradle.profiler.instrument.GradleInstrumentation;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LauncherConfigurationParser {
    public LaunchConfiguration calculate(Path studioInstallDir) {
        Path infoFile = studioInstallDir.resolve("Contents/Info.plist");
        Dict entries = PListParser.parse(infoFile);
        Dict jvmOptions = entries.dict("JVMOptions");
        List<Path> classPath = Arrays.stream(jvmOptions.string("ClassPath").split(":")).map(s -> FileSystems.getDefault().getPath(s.replace("$APP_PACKAGE", studioInstallDir.toString()))).collect(Collectors.toList());
        String mainClass = jvmOptions.string("MainClass");
        Map<String, String> systemProperties = mapValues(jvmOptions.dict("Properties").toMap(), v -> v.replace("$APP_PACKAGE", studioInstallDir.toString()));
        Path javaCommand = studioInstallDir.resolve("Contents/jre/jdk/Contents/Home/bin/java");
        Path agentJar = GradleInstrumentation.unpackPlugin("studio-agent").toPath();
        Path asmJar = GradleInstrumentation.unpackPlugin("asm").toPath();
        Path supportJar = GradleInstrumentation.unpackPlugin("instrumentation-support").toPath();
        Path protocolJar = GradleInstrumentation.unpackPlugin("client-protocol").toPath();
        return new LaunchConfiguration(javaCommand, classPath, systemProperties, mainClass, agentJar, supportJar, Arrays.asList(asmJar, protocolJar));
    }

    private static <T, S> Map<String, S> mapValues(Map<String, T> map, Function<T, S> mapper) {
        Map<String, S> result = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : map.entrySet()) {
            result.put(entry.getKey(), mapper.apply(entry.getValue()));
        }
        return result;
    }

    private static class Dict {
        private final Map<String, ?> contents;

        public Dict(Map<String, ?> contents) {
            this.contents = contents;
        }

        Dict dict(String key) {
            return (Dict) getEntry(key);
        }

        String string(String key) {
            return (String) getEntry(key);
        }

        Map<String, String> toMap() {
            return mapValues(contents, v -> (String) v);
        }

        private Object getEntry(String key) {
            Object value = contents.get(key);
            if (value == null) {
                throw new IllegalArgumentException(String.format("Dictionary does not contain entry '%s'.", key));
            }
            return value;
        }
    }

    private static class PListParser {
        private final XMLStreamReader reader;

        public PListParser(InputStream content) throws XMLStreamException {
            reader = XMLInputFactory.newFactory().createXMLStreamReader(content);
            while (reader.hasNext() && reader.getEventType() != XMLStreamReader.START_ELEMENT) {
                reader.next();
            }
        }

        public static Dict parse(Path infoFile) {
            try {
                try (InputStream inputStream = Files.newInputStream(infoFile)) {
                    return new PListParser(inputStream).plist();
                }
            } catch (IOException | XMLStreamException e) {
                throw new RuntimeException(String.format("Could not parse '%s'.", infoFile), e);
            }
        }

        public Dict plist() throws XMLStreamException {
            expectStart("plist");
            expectStart("dict");
            Dict result = dictEntries();
            expectEnd("dict");
            expectEnd("plist");
            return result;
        }

        private Dict dictEntries() throws XMLStreamException {
            Map<String, Object> entries = new LinkedHashMap<>();
            while (maybeStart("key")) {
                String key = expectText();
                expectEnd("key");
                Object value = readObject();
                entries.put(key, value);
            }
            return new Dict(entries);
        }

        private Object readObject() throws XMLStreamException {
            if (maybeStart("string")) {
                String text = expectText();
                expectEnd("string");
                return text;
            } else if (maybeStart("false")) {
                expectEnd("false");
                return false;
            } else if (maybeStart("true")) {
                expectEnd("true");
                return true;
            } else if (maybeStart("array")) {
                List<Object> elements = new ArrayList<>();
                while (true) {
                    if (maybeStart("dict")) {
                        elements.add(dictEntries());
                        expectEnd("dict");
                    } else if (maybeStart("string")) {
                        elements.add(expectText());
                        expectEnd("string");
                    } else {
                        break;
                    }
                }
                expectEnd("array");
                return elements;
            } else if (maybeStart("dict")) {
                Dict value = dictEntries();
                expectEnd("dict");
                return value;
            }
            throw broken("object");
        }

        private void expectStart(String name) throws XMLStreamException {
            if (!maybeStart(name)) {
                throw broken("<" + name + ">");
            }
            reader.next();
        }

        private boolean maybeStart(String name) throws XMLStreamException {
            while (reader.getEventType() != XMLStreamReader.START_ELEMENT) {
                if (reader.getEventType() == XMLStreamReader.SPACE) {
                    reader.next();
                } else {
                    break;
                }
            }
            if (reader.getEventType() != XMLStreamReader.START_ELEMENT || !reader.getLocalName().equalsIgnoreCase(name)) {
                return false;
            }
            reader.next();
            return true;
        }

        private void expectEnd(String name) throws XMLStreamException {
            while (reader.getEventType() != XMLStreamReader.END_ELEMENT) {
                if (reader.getEventType() == XMLStreamReader.SPACE) {
                    reader.next();
                } else {
                    break;
                }
            }
            if (reader.getEventType() != XMLStreamReader.END_ELEMENT || !reader.getLocalName().equalsIgnoreCase(name)) {
                throw broken("</" + name + ">");
            }
            reader.next();
        }

        private String expectText() throws XMLStreamException {
            if (reader.getEventType() != XMLStreamReader.CHARACTERS) {
                throw broken("text");
            }
            String result = reader.getText();
            reader.next();
            return result;
        }

        private IllegalStateException broken(String expected) {
            return new IllegalStateException(String.format("Expected %s at line %s, found %s.", expected, reader.getLocation().getLineNumber(), current()));
        }

        private String current() {
            switch (reader.getEventType()) {
                case XMLStreamReader.START_ELEMENT:
                    return "<" + reader.getLocalName() + ">";
                case XMLStreamReader.SPACE:
                case XMLStreamReader.CHARACTERS:
                    return "\"" + reader.getText() + "\"";
                default:
                    return "??";
            }
        }
    }
}
