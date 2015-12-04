package tw.com.unit;


import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import tw.com.EnvironmentSetupForTests;
import tw.com.FilesForTesting;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.exceptions.InvalidStackParameterException;
import tw.com.parameters.AutoDiscoverParams;
import tw.com.parameters.ProvidesZones;
import tw.com.repository.CloudFormRepository;
import tw.com.repository.VpcRepository;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TestAutoDiscoverParams extends EasyMockSupport implements ProvidesZones {
    private VpcRepository vpcRepository;
    private AutoDiscoverParams autoDiscover;
    private LinkedList<Parameter> results;
    private LinkedList<TemplateParameter> declaredParameters;

    @Before
    public void beforeEachTestRuns() {
        vpcRepository = createMock(VpcRepository.class);
        CloudFormRepository cfnRepository = createMock(CloudFormRepository.class);
        File templateFile = new File(FilesForTesting.SIMPLE_STACK_WITH_AZ);
        autoDiscover = new AutoDiscoverParams(templateFile, vpcRepository, cfnRepository);

        results = new LinkedList<>();
        declaredParameters = new LinkedList<>();
    }

    @Test
    public void shouldAddCorrectValueForTaggedParameter() throws IOException, CannotFindVpcException, InvalidStackParameterException {

        declaredParameters.add(new TemplateParameter().withDescription(AutoDiscoverParams.CFN_TAG_ON_OUTPUT).withParameterKey("paramKey"));
        EasyMock.expect(vpcRepository.getVpcTag("paramKey", EnvironmentSetupForTests.getMainProjectAndEnv())).andReturn("tagValue");

        replayAll();
        autoDiscover.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);
        verifyAll();

        assertEquals(1, results.size());
        Parameter result = results.getFirst();
        assertEquals("paramKey", result.getParameterKey());
        assertEquals("tagValue", result.getParameterValue());
    }

    @Test
    public void shouldAddCorrectValueForZone() throws IOException, CannotFindVpcException, InvalidStackParameterException {

        declaredParameters.add(new TemplateParameter().withDescription(AutoDiscoverParams.CFN_TAG_ZONE+"A").withParameterKey("paramKey"));

        replayAll();
        autoDiscover.addParameters(results, declaredParameters, EnvironmentSetupForTests.getMainProjectAndEnv(), this);
        verifyAll();

        assertEquals(1, results.size());
        Parameter result = results.getFirst();
        assertEquals("paramKey", result.getParameterKey());
        assertEquals("aviailabilityZoneA", result.getParameterValue());
    }

    @Override
    public Map<String, AvailabilityZone> getZones() {
        Map<String, AvailabilityZone> zones = new HashMap<>();
        zones.put("a", new AvailabilityZone().withZoneName("aviailabilityZoneA"));
        return zones;

    }
}
