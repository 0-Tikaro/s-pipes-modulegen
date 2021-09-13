package cz.cvut.spipes.modules;

import cz.cvut.spipes.constants.CSVW;
import cz.cvut.spipes.constants.KBSS_MODULE;
import cz.cvut.spipes.constants.SML;
import cz.cvut.spipes.engine.ExecutionContext;
import cz.cvut.spipes.engine.ExecutionContextFactory;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * no table group uri
 * only one file
 * no table uri TODO finish doc
 */
public class TabularToRDFModelModule extends AbstractModule {

    public static final String TYPE_URI = KBSS_MODULE.uri + "tabular";
    private static final Logger LOG = LoggerFactory.getLogger(TabularToRDFModelModule.class);

    /**
     * Output data mode.
     */
    public enum Mode {
        STANDARD,
        MINIMAL
    }

    //sml:dataPrefix
    public String dataPrefix;

    //TODO configure
    private Mode outputMode;

    //sml:replace
    private boolean isReplace;

    //sml:sourceFilePath
    private String sourceFilePath;

    //sml:delimiter
    private int delimiter;

    /**
     * Represent a root resource for group of tables.
     */
    private Resource G;

    /**
     * Represent a root resource for a table.
     */
    private Resource T;

    private Model outputModel;

    public boolean isReplace() {
        return isReplace;
    }

    public void setReplace(boolean replace) {
        isReplace = replace;
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public void setSourceFilePath(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
    }

    public int getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(int delimiter) {
        this.delimiter = delimiter;
    }

    public String getDataPrefix() {
        return dataPrefix;
    }

    public void setDataPrefix(String dataPrefix) {
        this.dataPrefix = dataPrefix;
    }

    @Override
    ExecutionContext executeSelf() {
        outputModel = ModelFactory.createDefaultModel();

        //TODO configure output mode
        outputMode = Mode.STANDARD;

        onTableGroup(null);

        onTable("file://" + sourceFilePath, null);

        CsvPreference csvPreference = new CsvPreference.Builder('"',
            delimiter,
            "\\n").build();

        // for each row
        ICsvListReader listReader = null;
        try {
            listReader = new CsvListReader(getFileReader(), csvPreference);

            String[] header = listReader.getHeader(true); // skip the header (can't be used with CsvListReader)

            List<String> row;
            int rowNumber = 0;
            while( (row = listReader.read()) != null ) {
                rowNumber++;

                // 4.6
                Resource R;
                if (outputMode == Mode.STANDARD) {
                    // 4.6.1
                    R = ResourceFactory.createResource();
                    // 4.6.2
                    outputModel.add(ResourceFactory.createStatement(
                            T,
                            ResourceFactory.createProperty(CSVW.hasRow.getURI()),
                            R));
                    // 4.6.3
                    outputModel.add(ResourceFactory.createStatement(
                            R,
                            ResourceFactory.createProperty(RDF.TYPE.toString()),
                            ResourceFactory.createResource(CSVW.Row.getURI())));
                    // 4.6.4
                    outputModel.add(ResourceFactory.createStatement(
                            R,
                            ResourceFactory.createProperty(CSVW.hasRowNum.getURI()),
                            ResourceFactory.createTypedLiteral(Integer.toString(rowNumber),
                                    XSDDatatype.XSDinteger)));
                    // 4.6.5
                    final String rowIri = T.getURI() + "#" + rowNumber;
                    outputModel.add(ResourceFactory.createStatement(
                            R,
                            ResourceFactory.createProperty(CSVW.hasUrl.getURI()),
                            ResourceFactory.createResource(rowIri)));
                    // 4.6.6 - Add titles.
                    // We do not support titles.

                    // 4.6.7
                    // In standard mode only, emit the triples generated by running
                    // the algorithm specified in section 6. JSON-LD to RDF over any
                    // non-core annotations specified for the row, with node R as
                    // an initial subject, the non-core annotation as property, and the
                    // value of the non-core annotation as value.
                } else {
                    R = null;
                }

                Resource rowResource = outputModel.createResource(dataPrefix + listReader.getRowNumber());

                for (int i = 0; i < header.length; i++) {
                    //TODO 4.6.8 (establish node S)
                    Resource S = ResourceFactory.createResource();
                    if (R == null) {
                        outputModel.add(getCellStatement(rowResource, header[i], row.get(i)));
                    } else {
                        //Standard mode - add links from table
                        outputModel.add(ResourceFactory.createStatement(
                                R,
                                ResourceFactory.createProperty(CSVW.hasDescribes.getURI()),
                                S));
                    }
                }
            }

        } catch (IOException e) {
            LOG.error("Error while reading file {}", sourceFilePath, e);
        } finally {
            if( listReader != null ) {
                try {
                    listReader.close();
                } catch (IOException e) {
                    LOG.error("Error while closing file {}", sourceFilePath, e);
                }
            }
        }

        return ExecutionContextFactory.createContext(outputModel);
    }

    @Override
    public String getTypeURI() {
        return TYPE_URI;
    }

    @Override
    public void loadConfiguration() {
        isReplace = this.getPropertyValue(SML.replace, false);
        sourceFilePath = getEffectiveValue(SML.sourceFilePath).asLiteral().toString();
        delimiter = getPropertyValue(SML.delimiter, '\t');
        dataPrefix = getEffectiveValue(SML.dataPrefix).asLiteral().toString();
    }

    private void onTableGroup(String tableGroupUri) {
        if (outputMode == Mode.STANDARD) {
            // 1
            if (tableGroupUri == null) {
                G = ResourceFactory.createResource();
            } else {
                G = ResourceFactory.createResource(tableGroupUri);
            }
            // 2
            outputModel.add(ResourceFactory.createStatement(
                    G,
                    ResourceFactory.createProperty(RDF.TYPE.toString()),
                    ResourceFactory.createResource(CSVW.TableGroup.getURI())));
            // 3
            // In standard mode only, emit the triples generated by running
            // the algorithm specified in section 6. JSON-LD to RDF over any
            // notes and non-core annotations specified for the group of tables,
            // with node G as an initial subject, the notes or non-core
            // annotation as property, and the value
            // of the notes or non-core annotation as value.
        }
    }

    private void onTable(String tableResource, String tableUri) {
        // 4 - we never skip table
        if (outputMode == Mode.STANDARD) {
            // 4.1 (establish a new node T which represents the current table)
            if (tableResource == null) {
                T = ResourceFactory.createResource();
            } else {
                T = ResourceFactory.createResource(tableResource);
            }
            // 4.2
            outputModel.add(ResourceFactory.createStatement(
                    G,
                    ResourceFactory.createProperty(CSVW.hasTable.getURI()),
                    T));
            // 4.3
            outputModel.add(ResourceFactory.createStatement(
                    T,
                    ResourceFactory.createProperty(RDF.TYPE.toString()),
                    ResourceFactory.createResource(CSVW.Table.getURI())));
            // 4.4 (specify the source tabular data file URL for the current table based on the url annotation)
            if (tableUri != null) {
                outputModel.add(ResourceFactory.createStatement(
                        G,
                        ResourceFactory.createProperty(CSVW.hasUrl.getURI()),
                        ResourceFactory.createResource(tableUri)));
            }
            // 4.5
            // In standard mode only, emit the triples generated by running
            // the algorithm specified in section 6. JSON-LD to RDF over any
            // notes and non-core annotations specified for the table, with
            // node T as an initial subject, the notes or non-core
            // annotation as property, and the value
            // of the notes or non-core annotation as value.
        }
    }

    private FileReader getFileReader() throws FileNotFoundException {
        return new FileReader(sourceFilePath);
    }

    private Statement getCellStatement(Resource rowResource, String columnName, String value) {
        return rowResource.getModel().createStatement(
            rowResource,
            ResourceFactory.createProperty(dataPrefix, columnName),
            ResourceFactory.createPlainLiteral(value)
        );
    }

    private Resource getResource(String name) {
        return ResourceFactory.createResource(dataPrefix + name);
    }
}
