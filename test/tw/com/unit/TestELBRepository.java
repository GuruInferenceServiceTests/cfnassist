package tw.com.unit;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.providers.CloudClient;
import tw.com.providers.LoadBalancerClient;
import tw.com.repository.ELBRepository;
import tw.com.repository.ResourceRepository;
import tw.com.repository.VpcRepository;

import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

@RunWith(EasyMockRunner.class)
public class TestELBRepository extends EasyMockSupport {
	
	ELBRepository repository;
	private LoadBalancerClient elbClient;
	private CloudClient cloudClient;
	private VpcRepository vpcRepository;
	private ResourceRepository cfnRepository;
	private ProjectAndEnv projAndEnv = new ProjectAndEnv("proj", "testEnv");
	
	@Before
	public void beforeEachTestRuns() {
		elbClient = createMock(LoadBalancerClient.class);
		cloudClient = createMock(CloudClient.class);
		vpcRepository  = createMock(VpcRepository.class);
		cfnRepository = createMock(ResourceRepository.class);
		
		repository = new ELBRepository(elbClient, cloudClient, vpcRepository, cfnRepository);
	}
	
	@Test
	public void ShouldThrowIfMoreThanOneELB() {
		List<LoadBalancerDescription> lbs = new LinkedList<LoadBalancerDescription>();
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb1Name").withVPCId("vpcId"));
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb2Name").withVPCId("vpcId"));
		
		Vpc vpc = new Vpc().withVpcId("vpcId");
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		
		replayAll();
		try {
			repository.findELBFor(projAndEnv );
			fail("should have thrown");
		}
		catch(TooManyELBException expectedException) {
			// no op
		}
		verifyAll();
	}
	
	@Test
	public void shouldFetchELBsForTheVPC() throws TooManyELBException {
		List<LoadBalancerDescription> lbs = new LinkedList<LoadBalancerDescription>();
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb1Name").withVPCId("someId"));
		lbs.add(new LoadBalancerDescription().withLoadBalancerName("lb2Name").withVPCId("vpcId"));

		Vpc vpc = new Vpc().withVpcId("vpcId");
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andReturn(vpc);
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(lbs);
		
		replayAll();
		LoadBalancerDescription result = repository.findELBFor(projAndEnv );
		assertEquals("lb2Name", result.getLoadBalancerName());
		verifyAll();
	}
	
	@Test
	public void shouldRegisterELBs() throws MustHaveBuildNumber, WrongNumberOfInstancesException, TooManyELBException {			
		Instance insA1 = new Instance().withInstanceId("instanceA1"); // initial
		Instance insA2 = new Instance().withInstanceId("instanceA2"); // initial
		Instance insB1 = new Instance().withInstanceId("instanceB1"); // new
		Instance insB2 = new Instance().withInstanceId("instanceB2"); // new
		Instance insB3 = new Instance().withInstanceId("instanceB3"); // new but wrong tags
		
		List<String> instanceIds = new LinkedList<String>();
		instanceIds.add(insA1.getInstanceId());
		instanceIds.add(insA2.getInstanceId());
		instanceIds.add(insB1.getInstanceId());
		instanceIds.add(insB2.getInstanceId());
		instanceIds.add(insB3.getInstanceId());
		
		List<Instance> instancesToAdd = new LinkedList<>();
		instancesToAdd.add(insB1);
		instancesToAdd.add(insB2);
		
		List<Instance> toRemove = new LinkedList<>();
		toRemove.add(insA1);
		toRemove.add(insA2);;
		
		String vpcId = "myVPC";
		String newBuildNumber = "0011";
		String oldBuildNumber = "0010";
		projAndEnv.addBuildNumber(newBuildNumber);

		List<LoadBalancerDescription> initalLoadBalancers = new LinkedList<LoadBalancerDescription>();
		initalLoadBalancers.add(new LoadBalancerDescription().withVPCId(vpcId).
				withInstances(insA1,insA2).
				withLoadBalancerName("lbName").withDNSName("dnsName"));
		
		List<LoadBalancerDescription> updatedLoadBalancers = new LinkedList<LoadBalancerDescription>();
		updatedLoadBalancers.add(new LoadBalancerDescription().withVPCId(vpcId).
				withInstances(insA1, insA2, insB1, insB2).
				withLoadBalancerName("lbName").withDNSName("dnsName"));
	
		EasyMock.expect(vpcRepository.getCopyOfVpc(projAndEnv)).andStubReturn(new Vpc().withVpcId(vpcId));
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(initalLoadBalancers);
		EasyMock.expect(cfnRepository.getInstancesFor(projAndEnv)).andReturn(instanceIds);
		EasyMock.expect(cloudClient.getTagsForInstance(insA1.getInstanceId())).andReturn(withTags(oldBuildNumber, "typeTag"));
		EasyMock.expect(cloudClient.getTagsForInstance(insA2.getInstanceId())).andReturn(withTags(oldBuildNumber, "typeTag"));
		EasyMock.expect(cloudClient.getTagsForInstance(insB1.getInstanceId())).andReturn(withTags(newBuildNumber, "typeTag"));
		EasyMock.expect(cloudClient.getTagsForInstance(insB2.getInstanceId())).andReturn(withTags(newBuildNumber, "typeTag"));
		EasyMock.expect(cloudClient.getTagsForInstance(insB3.getInstanceId())).andReturn(withTags(newBuildNumber, "xxxTag"));
		elbClient.registerInstances(instancesToAdd, "lbName");
		EasyMock.expectLastCall();
		EasyMock.expect(elbClient.describeLoadBalancers()).andReturn(updatedLoadBalancers);
		EasyMock.expect(elbClient.degisterInstancesFromLB(toRemove, "lbName")).andReturn(instancesToAdd);
		
		replayAll();
		List<Instance> result = repository.updateInstancesMatchingBuild(projAndEnv, "typeTag");
		assertEquals(2, result.size());
		assertEquals(insB1.getInstanceId(), result.get(0).getInstanceId());
		assertEquals(insB2.getInstanceId(), result.get(1).getInstanceId());
		verifyAll();		
		
	}
	
	private List<Tag> withTags(String buidNumber, String typeTag) {
		List<Tag> tags = new LinkedList<>();
		tags.add(new Tag().withKey(AwsFacade.BUILD_TAG).withValue(buidNumber));
		tags.add(new Tag().withKey(AwsFacade.TYPE_TAG).withValue(typeTag));
		return tags;
	}

	@Test
	public void shouldThrowIfNoBuildNumberIsGiven() throws MustHaveBuildNumber, WrongNumberOfInstancesException, TooManyELBException {
		replayAll();	
		try {
			repository.updateInstancesMatchingBuild(projAndEnv, "typeTag");
			fail("should have thrown");
		}
		catch(MustHaveBuildNumber expectedException) {
			// no op
		}
		verifyAll();	
	}

}
