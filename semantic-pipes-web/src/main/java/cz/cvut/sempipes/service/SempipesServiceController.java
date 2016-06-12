package cz.cvut.sempipes.service;

import cz.cvut.sempipes.eccairs.EccairsService;
import cz.cvut.sempipes.engine.*;
import cz.cvut.sempipes.modules.Module;
import cz.cvut.sempipes.modules.ModuleIdentity;
import cz.cvut.sempipes.util.RDFMimeType;
import cz.cvut.sempipes.util.RawJson;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

@RestController
@EnableWebMvc
public class SempipesServiceController {

    private static final Logger LOG = LoggerFactory.getLogger(SempipesServiceController.class);

    /**
     * Request parameter - 'id' of the module to be executed
     */
    public static final String P_ID = "_pId";

    /**
     * Request parameter - URL of the resource containing configuration
     * TODO redesign
     */
    public static final String P_CONFIG_URL = "_pConfigURL";

    /**
     * Input binding - URL of the file where input bindings are stored
     * TODO redesign
     */
    public static final String P_INPUT_BINDING_URL = "_pInputBindingURL";

    /**
     * Output binding - URL of the file where output bindings are stored
     * TODO redesign
     */
    public static final String P_OUTPUT_BINDING_URL = "_pOutputBindingURL";

    @RequestMapping(
            value = "/module",
            method = RequestMethod.GET,
            produces = {
                    RDFMimeType.LD_JSON_STRING,
                    RDFMimeType.N_TRIPLES_STRING,
                    RDFMimeType.RDF_XML_STRING,
                    RDFMimeType.TURTLE_STRING
            }
    )
    public Model processGetRequest(@RequestParam MultiValueMap<String,String> parameters) {
        LOG.info("Processing GET request.");
        return run(ModelFactory.createDefaultModel(), parameters);
    }

    @RequestMapping(
            value = "/module",
            method = RequestMethod.POST
            ,
            consumes = {
                    RDFMimeType.LD_JSON_STRING,
                    RDFMimeType.N_TRIPLES_STRING,
                    RDFMimeType.RDF_XML_STRING,
                    RDFMimeType.TURTLE_STRING
            },
            produces = {
                    RDFMimeType.LD_JSON_STRING,
                    RDFMimeType.N_TRIPLES_STRING,
                    RDFMimeType.RDF_XML_STRING,
                    RDFMimeType.TURTLE_STRING
            }
    )
    public Model processPostRequest(@RequestBody Model inputModel,
                                    @RequestParam MultiValueMap<String,String> parameters
    ) {
        LOG.info("Processing POST request.");
        return run(inputModel, parameters);
    }

    @RequestMapping(
            value = "/service",
            method = RequestMethod.GET,
            produces = {RDFMimeType.LD_JSON_STRING}
    ) // @ResponseBody
    public RawJson processServiceGetRequest(@RequestParam MultiValueMap parameters) {
        LOG.info("Processing service GET request.");
        return new RawJson(new EccairsService().run(new ByteArrayInputStream(new byte[]{}), "", parameters));
    }

    private QuerySolution transform(final Map parameters) {
        final QuerySolutionMap querySolution = new QuerySolutionMap();

        for (Object key : parameters.keySet()) {
            // TODO types of RDFNode
            String value = (String) ((List) parameters.get(key)).get(0);
            querySolution.add(key.toString(), ResourceFactory.createPlainLiteral(value));
        }

        return querySolution;
    }
    private Model run(final Model inputDataModel, final MultiValueMap<String,String> parameters ) {
        LOG.info("- parameters={}", parameters);

        if (!parameters.containsKey(P_ID)) {
            throw new SempipesServiceInvalidModuleIdException();
        }
        final String id = parameters.getFirst(P_ID);
        LOG.info("- id={}", id);

        // LOAD MODULE CONFIGURATION
        if (!parameters.containsKey(P_CONFIG_URL)) {
            throw new SempipesServiceInvalidConfigurationException();
        }
        final String configURL = parameters.getFirst(P_CONFIG_URL);
        LOG.info("- config URL={}", configURL);
        final Model configModel = ModelFactory.createDefaultModel();
        try {
            configModel.read(configURL, "TURTLE");
        } catch(Exception e) {
            throw new SempipesServiceInvalidConfigurationException();
        }

        // FILE WHERE TO GET INPUT BINDING
        URL inputBindingURL = null;
        if (parameters.containsKey(P_INPUT_BINDING_URL)) {
            try {
                inputBindingURL = new URL(parameters.getFirst(P_INPUT_BINDING_URL));
            } catch (MalformedURLException e) {
                throw new SempipesServiceInvalidOutputBindingException(e);
            }

            LOG.info("- input binding URL={}", inputBindingURL);
        }

        // FILE WHERE TO SAVE OUTPUT BINDING
        File outputBindingPath = null;
        if (parameters.containsKey(P_OUTPUT_BINDING_URL)) {
            try {
                final URL outputBindingURL = new URL(parameters.getFirst(P_OUTPUT_BINDING_URL));
                if (!outputBindingURL.getProtocol().equals("file")) {
                    throw new SempipesServiceInvalidOutputBindingException();
                }
                outputBindingPath = new File(outputBindingURL.toURI());
            } catch (MalformedURLException | URISyntaxException e) {
                throw new SempipesServiceInvalidOutputBindingException(e);
            }

            LOG.info("- output binding FILE={}", outputBindingPath);
        }

        parameters.remove(P_ID);
        parameters.remove(P_CONFIG_URL);
        parameters.remove(P_INPUT_BINDING_URL);
        parameters.remove(P_OUTPUT_BINDING_URL);

        // END OF PARAMETER PROCESSING
        final VariablesBinding inputVariablesBinding = new VariablesBinding(transform(parameters));
        try {
            if (inputBindingURL != null) {
                final VariablesBinding vb2 = new VariablesBinding();
                vb2.load(inputBindingURL.openStream(), "TURTLE");
                VariablesBinding vb3 = inputVariablesBinding.extendConsistently(vb2);
                if (vb3.isEmpty()) {
                    LOG.info("- no conflict between bindings loaded from '" + P_INPUT_BINDING_URL + "' and those provided in query string.");
                } else {
                    LOG.info("- conflicts found between bindings loaded from '" + P_INPUT_BINDING_URL + "' and those provided in query string: " + vb3.toString());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("- input variable binding ={}", inputVariablesBinding);

        // LOAD INPUT DATA
        ExecutionContext inputExecutionContext = ExecutionContextFactory.createContext(inputDataModel, inputVariablesBinding);

        ExecutionEngine engine = ExecutionEngineFactory.createEngine();
        ExecutionContext outputExecutionContext;
        Module module;
        // should execute module only
//        if (asArgs.isExecuteModuleOnly) {
        module = loadModule(configModel,id);

        if ( module == null ) {
            throw new SempipesServiceInvalidModuleIdException();
        }

        outputExecutionContext = engine.executeModule(module, inputExecutionContext);
//        } else {
//            module = PipelineFactory.loadPipeline(configModel.createResource(asArgs.configResourceUri));
//            outputExecutionContext = engine.executePipeline(module, inputExecutionContext);
//        }

        if ( outputBindingPath != null ) {
            try {
                outputExecutionContext.getVariablesBinding().save(new FileOutputStream(outputBindingPath), "TURTLE");
            } catch (IOException e) {
                throw new SempipesServiceInvalidOutputBindingException(e);
            }
        }

        LOG.info("Processing successfully finished.");
        return outputExecutionContext.getDefaultModel();
    }

    private Module loadModule(Model configModel, String id) {
        // TODO find in module registry ?!?
        if ( "http://onto.fel.cvut.cz/ontologies/sempipes/identity-transformer".equals(id)) {
            return new ModuleIdentity();
        } else {
            return PipelineFactory.loadModule(configModel.createResource(id));
        }
    }
}