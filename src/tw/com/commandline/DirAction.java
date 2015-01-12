package tw.com.commandline;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.OptionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.cloudformation.model.Parameter;

import tw.com.AwsFacade;
import tw.com.FacadeFactory;
import tw.com.entity.ProjectAndEnv;
import tw.com.entity.StackNameAndId;
import tw.com.exceptions.CfnAssistException;
import tw.com.exceptions.InvalidStackParameterException;

public class DirAction extends SharedAction {
	private static final Logger logger = LoggerFactory.getLogger(DirAction.class);

	@SuppressWarnings("static-access")
	public DirAction() {
		option = OptionBuilder.withArgName("dir").hasArg().
				withDescription("The directory/folder containing delta templates to apply").create("dir");
	}

	public void invoke(FacadeFactory factory, ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams, Collection<Parameter> artifacts, 
			String... args) throws FileNotFoundException, InvalidStackParameterException, IOException, CfnAssistException, InterruptedException, MissingArgumentException {
		AwsFacade aws = factory.createFacade();
		String folderPath = args[0];
		ArrayList<StackNameAndId> stackIds = aws.applyTemplatesFromFolder(folderPath , projectAndEnv, cfnParams);
		logger.info(String.format("Created %s stacks", stackIds.size()));
		for(StackNameAndId name : stackIds) {
			logger.info("Created stack " +name);
		}
	}

	@Override
	public void validate(ProjectAndEnv projectAndEnv, Collection<Parameter> cfnParams,
			Collection<Parameter> artifacts, String... argumentForAction)
			throws CommandLineException {
		guardForProjectAndEnv(projectAndEnv);		
		guardForNoBuildNumber(projectAndEnv);	
		guardForNoArtifacts(artifacts);
	}

	@Override
	public boolean usesProject() {
		return true;
	}

	@Override
	public boolean usesComment() {
		return true;
	}

	@Override
	public boolean usesSNS() {
		return true;
	}
}
		

