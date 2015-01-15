/*
 * -----------------------------------------------------------------------\
 * PerfCake
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package org.perfcake.reporting.destinations.chart;

import org.apache.commons.lang.StringUtils;
import org.perfcake.PerfCakeConst;
import org.perfcake.PerfCakeException;
import org.perfcake.reporting.Measurement;
import org.perfcake.reporting.ReportingException;
import org.perfcake.reporting.destinations.ChartDestination;
import org.perfcake.util.StringTemplate;
import org.perfcake.util.Utils;
import org.perfcake.validation.StringUtil;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Helper class for the ChartDestination. Handles all files and data manipulation to keep the destination small and clear.
 *
 * @author Martin Večeřa <marvenec@gmail.com>
 */
public class ChartDestinationHelper {

   private static final Logger log = Logger.getLogger(ChartDestinationHelper.class);

   private ChartDestination chartDestination;

   private String baseName;

   private File dataFile;

   private Path target;

   private boolean successInit = false;

   public ChartDestinationHelper(final ChartDestination chartDestination) {
      this.chartDestination = chartDestination;
      target = chartDestination.getTargetAsPath();

      successInit = createOutputFileStructure();

      if (successInit) {
         baseName = chartDestination.getGroup() + System.getProperty(PerfCakeConst.NICE_TIMESTAMP_PROPERTY);
         dataFile = Paths.get(target.toString(), "data", baseName + ".js").toFile();

         // write beginning of the data file
         successInit = writeDataFileHeader(); // successInit was already true

         // write .dat file for later use by loader
         final String loaderLine = writeChartDatFile();
         successInit = successInit && (loaderLine != null);

         // write a quick view file
         successInit = successInit && writeQuickView(loaderLine);
      }
   }

   private boolean createOutputFileStructure() {
      if (!target.toFile().exists()) {
         if (!target.toFile().mkdirs()) {
            log.error("Could not create output directory: " + target.toFile().getAbsolutePath());
            return false;
         }
      } else {
         if (!target.toFile().isDirectory()) {
            log.error("Could not create output directory. It already exists as a file: " + target.toFile().getAbsolutePath());
            return false;
         }
      }

      File dir = Paths.get(target.toString(), "data").toFile();
      if (!dir.exists() && !dir.mkdirs()) {
         log.error("Could not create data directory: " + dir.getAbsolutePath());
         return false;
      }

      dir = Paths.get(target.toString(), "src").toFile();
      if (!dir.exists() && !dir.mkdirs()) {
         log.error("Could not create source directory: " + dir.getAbsolutePath());
         return false;
      }

      try {
         Files.copy(getClass().getResourceAsStream("/charts/google-chart.js"), Paths.get(target.toString(), "src", "google-chart.js"), StandardCopyOption.REPLACE_EXISTING);
         Files.copy(getClass().getResourceAsStream("/charts/google-jsapi.js"), Paths.get(target.toString(), "src", "google-jsapi.js"), StandardCopyOption.REPLACE_EXISTING);
         Files.copy(getClass().getResourceAsStream("/charts/jquery.js"), Paths.get(target.toString(), "src", "jquery.js"), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
         log.error("Cannot copy necessary chart resources to the output path: ", e);
         return false;
      }

      return true;
   }

   private boolean writeDataFileHeader() {
      final StringBuilder dataHeader = new StringBuilder("var ");
      dataHeader.append(baseName);
      dataHeader.append(" = [ [ 'Time'");
      for (String attr : chartDestination.getAttributesAsList()) {
         dataHeader.append(", '");
         dataHeader.append(attr);
         dataHeader.append("'");
      }
      dataHeader.append(" ] ];\n\n");
      try {
         Utils.writeFileContent(dataFile, dataHeader.toString());
      } catch (IOException e) {
         log.error(String.format("Cannot write the data header to the chart data file %s: ", dataFile.getAbsolutePath()), e);
         return false;
      }

      return true;
   }

   private String writeChartDatFile() {
      final Path instructionsFile = Paths.get(target.toString(), "data", baseName + ".dat");
      final StringBuilder instructions = new StringBuilder("drawChart(");
      instructions.append(baseName);
      instructions.append(", 'chart_");
      instructions.append(baseName);
      instructions.append("_div', [0");
      for (int i = 1; i <= chartDestination.getAttributesAsList().size(); i++) {
         instructions.append(", ");
         instructions.append(i);
      }
      instructions.append("], '");
      instructions.append(chartDestination.getXAxis());
      instructions.append("', '");
      instructions.append(chartDestination.getYAxis());
      instructions.append("', '");
      instructions.append(chartDestination.getName());
      instructions.append("');\n");
      try {
         Utils.writeFileContent(instructionsFile, instructions.toString());
      } catch (IOException e) {
         log.error(String.format("Cannot write instructions file %s: ", instructionsFile.toFile().getAbsolutePath()), e);
         return null;
      }

      return instructions.toString();
   }

   private boolean writeQuickView(final String loaderLine) {
      final Path quickViewFile = Paths.get(target.toString(), "data", baseName + ".html");
      final Properties quickViewProps = new Properties();
      quickViewProps.setProperty("baseName", baseName);
      quickViewProps.setProperty("loader", loaderLine);
      try {
         StringTemplate quickView = new StringTemplate(new String(Files.readAllBytes(Paths.get(Utils.getResourceAsUrl("/charts/quick-view.html").toURI())), Utils.getDefaultEncoding()), quickViewProps);
         Utils.writeFileContent(quickViewFile, quickView.toString());
      } catch (IOException | PerfCakeException | URISyntaxException e) {
         log.error("Cannot store quick view file: ", e);
         return false;
      }

      return true;
   }

   /**
    * Obtains base name, loader entry from .dat file and column names from the .js file.
    *
    * @param datFile
    *       The original .dat file.
    * @param loaderEntries
    *       Map where to put loader lines.
    * @param columns
    *       Map where to put column names.
    * @throws java.io.IOException
    */
   private void parseEntry(final File datFile, final Map<String, String> names, final Map<String, String> loaderEntries, final Map<String, List<String>> columns) throws IOException {
      final String base = datFile.getName().substring(0, datFile.getName().length() - 4);
      final String loaderEntry = new String(Files.readAllBytes(Paths.get(datFile.toURI())));
      loaderEntries.put(base, loaderEntry);

      String name = loaderEntry.substring(loaderEntry.lastIndexOf(", ") + 3);
      name = name.substring(0, name.lastIndexOf("'"));
      names.put(base, name);

      final File jsFile = new File(datFile.getAbsolutePath().substring(0, datFile.getAbsolutePath().length() - 4) + ".js");
      String firstDataLine = "";
      try (BufferedReader br = Files.newBufferedReader(jsFile.toPath(), Charset.forName(Utils.getDefaultEncoding()));) {
         firstDataLine = br.readLine();
      }

      firstDataLine = firstDataLine.substring(firstDataLine.indexOf("[ [ ") + 4);
      firstDataLine = firstDataLine.substring(0, firstDataLine.indexOf(" ] ]"));
      String[] columnNames = firstDataLine.split(", ");
      List<String> columnsList = new ArrayList<>();
      for (String s : columnNames) {
         columnsList.add(StringUtil.trim(s, "'"));
      }
      columns.put(base, columnsList);
   }

   private String getLoadersHtml(final Map<String, String> loaderEntries) {
      final StringBuilder sb = new StringBuilder();
      for (String entry : loaderEntries.values()) {
         sb.append("         ");
         sb.append(entry);
      }
      return sb.toString();
   }

   private String getJsHtml(final Map<String, String> loaderEntries) {
      final StringBuilder sb = new StringBuilder();
      for (String entry : loaderEntries.keySet()) {
         sb.append("      <script type=\"text/javascript\" src=\"data/");
         sb.append(entry);
         sb.append(".js\"></script>\n");
      }
      return sb.toString();
   }

   private String getDivHtml(final Map<String, String> loaderEntries) {
      final StringBuilder sb = new StringBuilder();
      for (String entry : loaderEntries.keySet()) {
         sb.append("      <div id=\"chart_");
         sb.append(entry);
         sb.append("_div\"></div>\n");
      }
      return sb.toString();
   }

   private void writeIndex(final String loaders, final String js, final String div) throws PerfCakeException {
      final Path indexFile = Paths.get(target.toString(), "index.html");
      final Properties indexProps = new Properties();
      indexProps.setProperty("js", js);
      indexProps.setProperty("loader", loaders);
      indexProps.setProperty("chart", div);
      try {
         StringTemplate index = new StringTemplate(new String(Files.readAllBytes(Paths.get(Utils.getResourceAsUrl("/charts/index.html").toURI())), Utils.getDefaultEncoding()), indexProps);
         Utils.writeFileContent(indexFile, index.toString());
      } catch (IOException | URISyntaxException e) {
         throw new PerfCakeException("Unable to write index file: ", e);
      }
   }

   private List<String> findMatchingAttributes(final Map<String, List<String>> columns) {
      final List<String> seen = new ArrayList<>();
      final List<String> result = new ArrayList<>();

      for (List<String> cols : columns.values()) {
         for (String col : cols) {
            if (seen.contains(col)) {
               result.add(col);
            } else {
               seen.add(col);
            }
         }
      }

      return result;
   }

   private String combineResults(final String[] data, final Integer[] columns, final String chartName) throws PerfCakeException {
      final String base = StringUtils.join(data, "_");
      final String baseFile = "data-array-" + base + ".js";
      final String charts = StringUtils.join(data, ", ");
      StringBuilder cols = new StringBuilder();
      for (int col : columns) {
         if (cols.length() > 0) {
            cols.append(", ");
         }
         cols.append(col);
      }

      StringBuilder lens = new StringBuilder();
      StringBuilder quoted = new StringBuilder();
      for (String name : data) {
         if (lens.length() > 0) {
            lens.append(", ");
            quoted.append(", ");
         }
         lens.append(name);
         lens.append(".length");
         quoted.append("'");
         quoted.append(name);
         quoted.append("'");
      }

      final Path dataFile = Paths.get(target.toString(), "data", baseFile);
      final Properties dataProps = new Properties();
      dataProps.setProperty("baseName", base);
      dataProps.setProperty("chartCols", cols.toString());
      dataProps.setProperty("chartLen", lens.toString());
      dataProps.setProperty("chartsQuoted", quoted.toString());
      dataProps.setProperty("charts", charts);
      try {
         StringTemplate index = new StringTemplate(new String(Files.readAllBytes(Paths.get(Utils.getResourceAsUrl("/charts/data-array.js").toURI())), Utils.getDefaultEncoding()), dataProps);
         Utils.writeFileContent(dataFile, index.toString());
      } catch (IOException | URISyntaxException e) {
         throw new PerfCakeException("Unable to write combined data file: ", e);
      }

      return base;
   }

   private void analyzeMatchingCharts(final Map<String, String> names, final Map<String, String> loaderEntries, final Map<String, List<String>> columns) throws PerfCakeException {
      final List<String> matches = findMatchingAttributes(columns);

      for (String match : matches) {
         final List<String> data = new ArrayList<>();
         final List<String> colNames = new ArrayList<>();
         final List<Integer> cols = new ArrayList<>();
         final String chartName = "Results for " + match;
         colNames.add("Time");

         for (Map.Entry<String, List<String>> entry : columns.entrySet()) {
            if (entry.getValue().contains(match)) {
               data.add(entry.getKey());
               cols.add(entry.getValue().indexOf(match));
               colNames.add(names.get(entry.getKey()));
            }
         }

         String base = combineResults(data.toArray(new String[data.size()]), cols.toArray(new Integer[cols.size()]), chartName);
         names.put(base, chartName);
         loaderEntries.put(base, "loadit");
         columns.put(base, colNames);
      }
   }

   /**
    * Creates the main index.html file based on all previously generated reports in the same directory.
    *
    * @return True if and only if the whole operation succeeds.
    * @throws java.io.IOException
    */
   public void compileResults() throws PerfCakeException {
      final File outputDir = Paths.get(target.toString(), "data").toFile();
      final Map<String, String> names = new HashMap<>(); // baseId -> chart title
      final Map<String, String> loaderEntries = new HashMap<>(); // baseId -> loader entry
      final Map<String, List<String>> columns = new HashMap<>(); // baseId -> [columns]

      final List<File> files = Arrays.asList(outputDir.listFiles(new FileFilter() {
         @Override
         public boolean accept(final File pathname) {
            return pathname.getName().toLowerCase().endsWith(".dat");
         }
      }));

      try {
         for (final File f : files) {
            parseEntry(f, names, loaderEntries, columns);
         }

         analyzeMatchingCharts(names, loaderEntries, columns);
      } catch (IOException e) {
         throw new PerfCakeException("Unable to parse stored results: ", e);
      }

      writeIndex(getLoadersHtml(loaderEntries), getJsHtml(loaderEntries), getDivHtml(loaderEntries));
   }

   private String getResultLine(final Measurement m) {
      StringBuilder sb = new StringBuilder();
      sb.append(baseName);
      sb.append(".push(['");
      sb.append(Utils.timeToHMS(m.getTime()));
      sb.append("'");

      for (String attr : chartDestination.getAttributesAsList()) {
         sb.append(", ");
         Object data = m.get(attr);

         // we do not have all required attributes, return an empty line
         if (data == null) {
            return "";
         }

         if (data instanceof String) {
            sb.append("'");
            sb.append((String) data);
            sb.append("'");
         } else {
            sb.append(data.toString());
         }
      }

      sb.append("]);\n");

      return sb.toString();
   }

   public void appendResult(final Measurement m) throws ReportingException {
      String line = getResultLine(m);

      if (line != null && !"".equals(line)) {
         try (FileOutputStream fos = new FileOutputStream(dataFile, true); OutputStreamWriter osw = new OutputStreamWriter(fos, Utils.getDefaultEncoding()); BufferedWriter bw = new BufferedWriter(osw)) {
            bw.append(line);
         } catch (IOException ioe) {
            throw new ReportingException(String.format("Could not append data to the chart file %s.", dataFile.getAbsolutePath()), ioe);
         }
      }
   }

   public boolean isSuccessInit() {
      return successInit;
   }
}
