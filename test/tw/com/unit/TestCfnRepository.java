package tw.com.unit;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.easymock.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.Tag;

import tw.com.AwsFacade;
import tw.com.EnvironmentSetupForTests;
import tw.com.MonitorStackEvents;
import tw.com.PollingStackMonitor;
import tw.com.StackMonitor;
import tw.com.entity.EnvironmentTag;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackEntry;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.InvalidParameterException;
import tw.com.exceptions.NotReadyException;
import tw.com.exceptions.WrongNumberOfStacksException;
import tw.com.providers.CloudFormationClient;
import tw.com.repository.CfnRepository;

@RunWith(EasyMockRunner.class)
public class TestCfnRepository extends EasyMockSupport {
	
	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);

    private CloudFormationClient formationClient;
	private CfnRepository repository;

	private EnvironmentTag envTag; 
	
	@Before
	public void beforeEachTestRuns() {
		formationClient = createMock(CloudFormationClient.class);
		repository = new CfnRepository(formationClient, mainProjectAndEnv.getProject());	
		envTag = new EnvironmentTag(EnvironmentSetupForTests.ENV);
	}
	
	@Test
	public void testGetAllStacks() {
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("");
		List<Stack> list = new LinkedList<Stack>();
		list.add(new Stack().withTags(tags).withStackName("matchingStack"));
		list.add(new Stack().withStackName("noMatchingTags"));
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		replayAll();
			
		List<StackEntry> results = repository.getStacks();
		assertEquals(1, results.size());
		results = repository.getStacks(); // second call should hit cache
		assertEquals(1, results.size());

		verifyAll();	
	}
	
	@Test
	public void testGetStacksByEnv() {
		List<Tag> tagsA = EnvironmentSetupForTests.createExpectedStackTags("");
		List<Tag> tagsB = new LinkedList<Tag>();
		tagsB.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_ENV", "WrongTest"));
		tagsB.add(EnvironmentSetupForTests.createCfnStackTAG("CFN_ASSIST_PROJECT", "CfnAssist"));
		
		List<Stack> list = new LinkedList<Stack>();
		list.add(new Stack().withTags(tagsA).withStackName("matchingStack"));
		list.add(new Stack().withStackName("wrongEnvStack").withTags(tagsB));
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		replayAll();
			
		List<StackEntry> results = repository.getStacks(envTag);
		assertEquals(1, results.size());
		results = repository.getStacks(envTag); // second call should hit cache
		assertEquals(1, results.size());

		verifyAll();	
	}
	
	@Test
	public void testShouldMatchOnBuildNumberAndEnv() {
		String BuildNumber = "0042";
		
		List<Tag> tagsA = EnvironmentSetupForTests.createExpectedStackTags("");
		List<Tag> tagsB = EnvironmentSetupForTests.createExpectedStackTags("");
		tagsB.add(EnvironmentSetupForTests.createCfnStackTAG(AwsFacade.BUILD_TAG,"0052"));
		List<Tag> tagsC = EnvironmentSetupForTests.createExpectedStackTags("");
		tagsC.add(EnvironmentSetupForTests.createCfnStackTAG(AwsFacade.BUILD_TAG,BuildNumber));
		
		List<Stack> list = new LinkedList<Stack>();
		list.add(new Stack().withTags(tagsA).withStackName("noBuildNumber"));
		list.add(new Stack().withTags(tagsB).withStackName("wrongBuildNumber"));
		list.add(new Stack().withTags(tagsC).withStackName("matchingBuildNumber"));
		
		String buildNumber = BuildNumber;
			
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		replayAll();
			
		List<StackEntry> results = repository.stacksMatchingEnvAndBuild(envTag, buildNumber);
		assertEquals(1, results.size());
		results = repository.stacksMatchingEnvAndBuild(envTag, buildNumber); // second call should hit cache
		assertEquals(1, results.size());

		verifyAll();	
	}
	
	@Test 
	public void testShouldFindResourcesInsideOfAStack()  {
		String logicalId = "logicalIdOfResource";
		String expectedPhyId = "physicalIdOfResource";
		String stackName = "testStackName";
		
		List<StackResource> stackResources = new LinkedList<>();
		stackResources.add(new StackResource().withLogicalResourceId("logWrong").withPhysicalResourceId("phyWrong"));
		stackResources.add(new StackResource().withLogicalResourceId(logicalId).withPhysicalResourceId(expectedPhyId));
		
		List<Stack> list = new LinkedList<Stack>();
		list.add(new Stack().
				withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName));
		
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(stackResources);
		replayAll();
		
		String physicalId = repository.findPhysicalIdByLogicalId(envTag, logicalId);		
		assertEquals(expectedPhyId, physicalId);
		physicalId = repository.findPhysicalIdByLogicalId(envTag, logicalId);	 // hits cache	
		assertEquals(expectedPhyId, physicalId);
		
		verifyAll();
	}
	
	@Test
	public void testShouldWaitForStatusToChange() throws WrongNumberOfStacksException, InterruptedException {
		String stackName = "testStack";
		
		Stack inProgressStack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackStatus(StackStatus.CREATE_IN_PROGRESS);
		
		Stack doneStack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackStatus(StackStatus.CREATE_COMPLETE);
		
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(doneStack);	
		
		replayAll();
		String result = repository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(StackMonitor.CREATE_ABORTS));
		assertEquals(StackStatus.CREATE_COMPLETE.toString(), result);
		verifyAll();
	}
	
	@Test
	public void testWaitForStatusShouldAbort() throws WrongNumberOfStacksException, InterruptedException {
		String stackName = "testStack";
		
		Stack inProgressStack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackStatus(StackStatus.CREATE_IN_PROGRESS);
		
		Stack abortedStack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackStatus(StackStatus.CREATE_FAILED);
		
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(abortedStack);	
		
		replayAll();
		String result = repository.waitForStatusToChangeFrom(stackName, StackStatus.CREATE_IN_PROGRESS, Arrays.asList(StackMonitor.CREATE_ABORTS));
		assertEquals(StackStatus.CREATE_FAILED.toString(), result);
		verifyAll();
	}
	
	@Test
	public void testShouldCheckIfStackExists() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		
		Stack stack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName);
		
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(stack);
		replayAll();
		assertTrue(repository.stackExists(stackName));
		verifyAll();
	}
	
	@Test
	public void testShouldCheckIfStackExistsButMissing() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		
		AmazonServiceException amazonServiceException = new AmazonServiceException("exceptionText");
		amazonServiceException.setStatusCode(400);
		EasyMock.expect(formationClient.describeStack(stackName)).andThrow(amazonServiceException);
		replayAll();
		assertFalse(repository.stackExists(stackName));
		verifyAll();
	}
	
	@Test
	public void testShouldCheckIfStackExistsThrowsIfNotA400() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		
		AmazonServiceException amazonServiceException = new AmazonServiceException("exceptionText");
		amazonServiceException.setStatusCode(500);
		EasyMock.expect(formationClient.describeStack(stackName)).andThrow(amazonServiceException);
		replayAll();
		try {
			repository.stackExists(stackName);
			fail("should have thrown");
		}
		catch(AmazonServiceException expectedThisException) {
			// expected
		}
		verifyAll();
	}
	
	@Test
	public void testShouldGetCurrentStackStatus() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		Stack inProgressStack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackStatus(StackStatus.CREATE_IN_PROGRESS);
		
		Stack abortedStack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName("someOtherName").
				withStackStatus(StackStatus.CREATE_FAILED);
		
		List<Stack> stacks = new LinkedList<Stack>();
		stacks.add(inProgressStack);
		stacks.add(abortedStack);
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks); // cached after first call
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(inProgressStack);
		
		replayAll();
		String result = repository.getStackStatus(stackName);
		assertEquals(StackStatus.CREATE_IN_PROGRESS.toString(), result);
		result = repository.getStackStatus(stackName);
		assertEquals(StackStatus.CREATE_IN_PROGRESS.toString(), result);
		verifyAll();
	}
	
	@Test
	public void emptyStatusIfNoSuchStack() throws FileNotFoundException, WrongNumberOfStacksException, NotReadyException, IOException, InvalidParameterException, InterruptedException {			
		List<Stack> stacks = new LinkedList<Stack>();
		stacks.add(new Stack().withStackName("ThisIsNotTheStackYouAreLookingFor"));
			
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(stacks); 
		replayAll();
		
		String result = repository.getStackStatus("thisStackShouldNotExist");		
		assertEquals(0, result.length());
		verifyAll();
	}
	
	@Test
	public void testShouldGetIdForAStackName() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		Stack stack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackId(stackId);
		
		EasyMock.expect(formationClient.describeStack(stackName)).andReturn(stack);
		
		replayAll();
		StackNameAndId result = repository.getStackId(stackName);
		assertEquals(stackName, result.getStackName());
		assertEquals(stackId, result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldFindTheInstancesForAStack() {
		String stackName = "testStack";
		String stackId = "theIdOfTheStack";
		String instanceId = "theInstanceId";
		
		List<StackResource> resources = new LinkedList<StackResource>();
		resources.add(new StackResource().withResourceType("AWS::EC2::Instance").withPhysicalResourceId(instanceId));
		resources.add(new StackResource().withResourceType("AWS::EC2::ELB").withPhysicalResourceId("wrongId"));
		
		Stack stack = new Stack().withTags(EnvironmentSetupForTests.createExpectedStackTags("")).
				withStackName(stackName).
				withStackId(stackId).
				withStackStatus(StackStatus.CREATE_COMPLETE);
		
		List<Stack> list = new LinkedList<>();
		list.add(stack);
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		EasyMock.expect(formationClient.describeStackResources(stackName)).andReturn(resources);
		replayAll();
		
		List<String> result = repository.getInstancesFor(EnvironmentSetupForTests.getMainProjectAndEnv());
		assertEquals(result.size(),1);
		result = repository.getInstancesFor(EnvironmentSetupForTests.getMainProjectAndEnv()); // cached call
		assertEquals(result.size(),1);

		assertEquals(instanceId, result.get(0));
		verifyAll();
	}
	
	@Test
	public void testShouldGetStackByName() throws WrongNumberOfStacksException {
		String stackName = "testStack";
		
		List<Stack> list = new LinkedList<>();
		List<Tag> tags = EnvironmentSetupForTests.createExpectedStackTags("");
		list.add(new Stack().withStackName(stackName).withStackId("correctId").withTags(tags ));
		list.add(new Stack().withStackName("someOtherName").withStackId("wrongId").withTags(tags));
		EasyMock.expect(formationClient.describeAllStacks()).andReturn(list);
		
		replayAll();
		Stack result = repository.getStack(stackName);
		assertEquals(stackName, result.getStackName());
		assertEquals("correctId", result.getStackId());
		result = repository.getStack(stackName); // cached on this call
		assertEquals(stackName, result.getStackName());
		assertEquals("correctId", result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldDeleteStack() {
		// this smells, at least until we pull cache updates down into repository
		
		formationClient.deleteStack("stackName");
		EasyMock.expectLastCall();
		replayAll();
		
		repository.deleteStack("stackName");
		verifyAll();
	}
	
	@Test
	public void shouldCreateStack() throws NotReadyException {
		// this smells, at least until we pull cache updates down into repository
		
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		MonitorStackEvents monitor = new PollingStackMonitor(repository);
		ProjectAndEnv projAndEnv = EnvironmentSetupForTests.getMainProjectAndEnv();
		EasyMock.expect(formationClient.createStack(projAndEnv, "contents", "stackName", 
				parameters, monitor, "comment")).andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.createStack(projAndEnv, "contents", "stackName", parameters, monitor, "comment");
		assertEquals("stackName", result.getStackName());
		assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
	
	@Test
	public void shouldUpdateStack() throws NotReadyException, WrongNumberOfStacksException, InvalidParameterException {
		// this smells, at least until we pull cache updates down into repository
		
		Collection<Parameter> parameters = new LinkedList<Parameter>();
		MonitorStackEvents monitor = new PollingStackMonitor(repository);
		EasyMock.expect(formationClient.updateStack("contents", parameters, monitor, "stackName")).andReturn(new StackNameAndId("stackName", "someStackId"));
		replayAll();
		
		StackNameAndId result = repository.updateStack("contents", parameters, monitor, "stackName");
		assertEquals("stackName", result.getStackName());
		assertEquals("someStackId", result.getStackId());
		verifyAll();
	}
		
}
