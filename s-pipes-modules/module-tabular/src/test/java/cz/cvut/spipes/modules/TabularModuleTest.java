package cz.cvut.spipes.modules;

import cz.cvut.spipes.config.ExecutionConfig;
import cz.cvut.spipes.constants.CSVW;
import cz.cvut.spipes.engine.ExecutionContext;
import cz.cvut.spipes.engine.ExecutionContextFactory;
import cz.cvut.spipes.exception.ResourceNotUniqueException;
import cz.cvut.spipes.modules.exception.TableSchemaException;
import cz.cvut.spipes.test.JenaTestUtils;
import cz.cvut.spipes.util.JenaUtils;
import cz.cvut.spipes.util.StreamResourceUtils;
import org.apache.jena.rdf.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

 class TabularModuleTest extends AbstractModuleTestHelper {

    private TabularModule module;
    private final String DATA_PREFIX = "http://onto.fel.cvut.cz/data/";

    private static final Logger LOG = LoggerFactory.getLogger(TabularModuleTest.class);

    @BeforeEach
     void setUp() {
        module = new TabularModule();

        module.setReplace(true);
        module.setDelimiter('\t');
        module.setQuoteCharacter('"');
        module.setDataPrefix(DATA_PREFIX);
        module.setOutputMode(Mode.STANDARD);

        module.setInputContext(ExecutionContextFactory.createEmptyContext());
    }

    @Test
     void executeWithSimpleTransformation() throws URISyntaxException, IOException {
        module.setSourceResource(
            StreamResourceUtils.getStreamResource(
                "http://test-file",
                getFilePath("countries.tsv"))
        );

        ExecutionContext outputContext = module.executeSelf();

        assertTrue(outputContext.getDefaultModel().size() > 0);
    }

    @Test
     void executeWithDuplicateColumnsThrowsResourceNotUniqueException()
            throws URISyntaxException, IOException {
        module.setSourceResource(StreamResourceUtils.getStreamResource(
                "http://test-file-2",
                getFilePath("duplicate_column_countries.tsv"))
        );

        ResourceNotUniqueException exception = assertThrows(
                ResourceNotUniqueException.class,
                module::executeSelf
        );

        String expectedMessage = "latitude";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
     void checkDefaultConfigurationAgainstExemplaryModelOutput() throws URISyntaxException, IOException {
        module.setSourceResource(
                StreamResourceUtils.getStreamResource(
                        "http://test-file",
                        getFilePath("countries.tsv"))
        );

        ExecutionContext outputContext = module.executeSelf();
        Model actualModel = outputContext.getDefaultModel();
        Model expectedModel = ModelFactory.createDefaultModel()
                .read(getFilePath("countries-model-output.ttl").toString());

        assertTrue(actualModel.isIsomorphicWith(expectedModel));
    }


     @DisplayName("Executes Tabular module with or without csvw:property.")
     @ParameterizedTest(name = "{index} => message=''Test {0} (csvw:property) in the schema''")
     @ValueSource(strings = {"withProperty", "withoutProperty"})
     void executeSelfChecksSchemaWithoutProperty(String folderName) throws URISyntaxException, IOException {
        module.setSourceResource(
                StreamResourceUtils.getStreamResource(DATA_PREFIX, getFilePath("examples/" + folderName + "/input.tsv"))
        );

        Model inputModel = JenaTestUtils.laodModelFromResource("/examples/" + folderName + "/input-data-schema.ttl");
        module.setInputContext(ExecutionContextFactory.createContext(inputModel));

        ExecutionContext outputContext = module.executeSelf();
        Model actualModel = outputContext.getDefaultModel();

        Model expectedModel = ModelFactory.createDefaultModel()
                .read(getFilePath("examples/" + folderName + "/expected-output.ttl").toString());
        assertTrue(actualModel.isIsomorphicWith(expectedModel));
    }



    @DisplayName("Executes Tabular module with the different number of columns in the schema.")
    @ParameterizedTest(name = "{index} => message=''{0} in the schema''")
    @ValueSource(strings = {"moreColumns", "lessColumns", "noColumns"})
    void executeSelfThrowsException(String folderName) throws URISyntaxException, IOException {
        assumeTrue(ExecutionConfig.isExitOnError());
        module.setSourceResource(
                StreamResourceUtils.getStreamResource(DATA_PREFIX,getFilePath("examples/" + folderName + "/input.tsv"))
        );

        Model inputModel = JenaTestUtils.laodModelFromResource("/examples/" + folderName + "/input-data-schema.ttl");
        module.setInputContext(ExecutionContextFactory.createContext(inputModel));

        assertThrows(TableSchemaException.class, () -> module.executeSelf());
    }

    @Test
    void executeSelfWithDataSchemaNoHeaderReturnsNamedColumnsFromSchema()
            throws URISyntaxException, IOException {
        module.setSkipHeader(true);

        module.setSourceResource(
                StreamResourceUtils.getStreamResource(DATA_PREFIX,getFilePath("examples/noHeader/schemaExample/input.csv"))
        );

        Model inputModel = JenaTestUtils.laodModelFromResource("/examples/noHeader/schemaExample/input-data-schema.ttl");
        module.setInputContext(ExecutionContextFactory.createContext(inputModel));

        ExecutionContext outputContext = module.executeSelf();

        String[] columns = new String[]{"col_1", "col_2", "col_3", "col_4", "col_5"};


        for (int i = 2; i <= 4; i++) {
            Resource resource = ResourceFactory.createResource(DATA_PREFIX + "#row-" + i);
            for (String column: columns) {
                Property property = ResourceFactory.createProperty(DATA_PREFIX, column);
                assertTrue(outputContext.getDefaultModel().contains(resource, property));
            }
        }
    }

    @Test
    void executeSelfWithNoDataSchemaNoHeaderReturnsAutonamedColumns()
            throws URISyntaxException, IOException {
        module.setSkipHeader(true);

        module.setSourceResource(
                StreamResourceUtils
                        .getStreamResource(DATA_PREFIX,getFilePath("examples/noHeader/noSchemaExample/input.tsv"))
        );

        ExecutionContext outputContext = module.executeSelf();

        for (int i = 2; i <= 4; i++) {
            Resource resource = ResourceFactory.createResource(DATA_PREFIX + "#row-" + i);
            for (int j = 1; j <= 6; j++) {
                String columnName = "column_" + j;
                Property property = ResourceFactory.createProperty(DATA_PREFIX, columnName);

                assertTrue(outputContext.getDefaultModel().contains(resource, property));
            }
        }
    }

     @Test
     void executeSelfWithBNodesInSchema() throws IOException, URISyntaxException {
         module.setSourceResource(
                 StreamResourceUtils.getStreamResource(DATA_PREFIX, getFilePath("examples/blankNodes/input.tsv"))
         );

         Model inputModel = JenaTestUtils.laodModelFromResource("/examples/blankNodes/input-data-schema.ttl");
         module.setInputContext(ExecutionContextFactory.createContext(inputModel));

         ExecutionContext outputContext = module.executeSelf();
         Model actualModel = outputContext.getDefaultModel();

         Model expectedModel = ModelFactory.createDefaultModel()
                 .read(getFilePath("examples/blankNodes/expected-output.ttl").toString());

         assertIsomorphic(actualModel, expectedModel);
     }

     @Test
     void executeSelfWithHTMLFileInput() throws URISyntaxException, IOException {
         module.setProcessHTMLFile(true);
         module.setSourceResource(
                StreamResourceUtils.getStreamResource(DATA_PREFIX, getFilePath("examples/htmlFile/input.html"))
        );

        ExecutionContext outputContext = module.executeSelf();
        Model actualModel = outputContext.getDefaultModel();

        List<String> header = Arrays.asList("No_", "Test_1", "Test_2", "Description");
        List<List<String>> rows = Arrays.asList(
                Arrays.asList("1.", "123", "456", "description 1"),
                Arrays.asList("2.", "789", "123", "description 2"));

        header.forEach(headerValue -> assertTrue(actualModel.contains(null, CSVW.name, headerValue)));

        for (List<String> row: rows){
            for(int idx = 0; idx < header.size(); idx++) {
                String headerValue = header.get(idx);
                String rowValue = row.get(idx);
                assertTrue(actualModel
                        .contains(null, actualModel.getProperty(DATA_PREFIX + headerValue), rowValue)
                );
            }
        }
     }

    void assertIsomorphic(Model actualModel, Model expectedModel){
        if (! actualModel.isIsomorphicWith(expectedModel)) {
            LOG.debug("Saving actual model ... ");
            JenaUtils.saveModelToTemporaryFile(actualModel);
            LOG.debug("Saving expected model ... ");
            JenaUtils.saveModelToTemporaryFile(expectedModel);
            fail("Actual model is not isomorphic with expected model (see additional information above).");
        }
    }

    @Override
    public String getModuleName() {
        return "tabular";
    }

    public Path getFilePath(String fileName) throws URISyntaxException {
        return Paths.get(getClass().getResource("/" + fileName).toURI());
    }
}