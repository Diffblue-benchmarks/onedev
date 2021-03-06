package io.onedev.server.ci.job;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.NotEmpty;

import io.onedev.server.ci.Dependency;
import io.onedev.server.ci.job.cache.JobCache;
import io.onedev.server.ci.job.log.LogLevel;
import io.onedev.server.ci.job.outcome.JobOutcome;
import io.onedev.server.ci.job.trigger.JobTrigger;
import io.onedev.server.event.ProjectEvent;
import io.onedev.server.util.inputspec.InputSpec;
import io.onedev.server.util.validation.Validatable;
import io.onedev.server.util.validation.annotation.ClassValidating;
import io.onedev.server.web.editable.annotation.Editable;
import io.onedev.server.web.editable.annotation.Horizontal;

@Editable
@Horizontal
@ClassValidating
public class Job implements Serializable, Validatable {

	private static final long serialVersionUID = 1L;
	
	private String name;
	
	private String environment;
	
	private List<String> commands;
	
	private boolean cloneSource = true;
	
	private List<JobOutcome> outcomes = new ArrayList<>();

	private List<JobCache> caches = new ArrayList<>();
	
	private List<Dependency> dependencies = new ArrayList<>();
	
	private List<InputSpec> promptParams = new ArrayList<>();
	
	private List<JobTrigger> triggers = new ArrayList<>();
	
	private long timeout = 3600;
	
	private LogLevel logLevel = LogLevel.INFO;
	
	@Editable(order=100, description="Specify name of the job")
	@NotEmpty
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Editable(order=110, description="Specify the environment to run the command. Environment will be interpretated "
			+ "by underlying job executor. For instance, a docker executor will treat it as a docker image, and an "
			+ "agent executor will treat it as labels to match agents")
	@NotEmpty
	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	@Editable(order=120, description="Specify commands to execute in above environment, with one command per line. "
			+ "For Windows based environments, commands will be interpretated by PowerShell, and for Unix/Linux "
			+ "based environments, commands will be interpretated by shell")
	@Size(min=1, message="At least one command needs to be specified")
	public List<String> getCommands() {
		return commands;
	}

	public void setCommands(List<String> commands) {
		this.commands = commands;
	}
	
	@Editable(order=130, description="Whether or not to clone the source code")
	public boolean isCloneSource() {
		return cloneSource;
	}

	public void setCloneSource(boolean cloneSource) {
		this.cloneSource = cloneSource;
	}	
	
	@Editable(order=150, description="Cache specific paths to speed up job execution. For instance for node.js "
			+ "projects, you may cache the <tt>node_modules</tt> folder to avoid downloading node modules for "
			+ "subsequent job executions. Note that cache is considered as a best-effort approach and your "
			+ "build script should always consider that cache might not be available")
	public List<JobCache> getCaches() {
		return caches;
	}

	public void setCaches(List<JobCache> caches) {
		this.caches = caches;
	}

	@Editable(order=200, description="Specify job outcomes")
	public List<JobOutcome> getOutcomes() {
		return outcomes;
	}

	public void setOutcomes(List<JobOutcome> outcomes) {
		this.outcomes = outcomes;
	}

	@Editable(order=300, description="Specify parameters to prompt when the job "
			+ "is triggered manually")
	public List<InputSpec> getPromptParams() {
		return promptParams;
	}

	public void setPromptParams(List<InputSpec> promptParams) {
		this.promptParams = promptParams;
	}

	@Editable(name="Dependency Jobs", order=400, description="Job dependencies determines the order and "
			+ "concurrency when run different jobs")
	public List<Dependency> getDependencies() {
		return dependencies;
	}

	public void setDependencies(List<Dependency> dependencies) {
		this.dependencies = dependencies;
	}

	@Editable(order=500, description="Use triggers to run the job automatically under certain conditions")
	public List<JobTrigger> getTriggers() {
		return triggers;
	}

	public void setTriggers(List<JobTrigger> triggers) {
		this.triggers = triggers;
	}

	@Editable(order=700, description="Specify timeout in seconds")
	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	@Editable(order=800)
	public LogLevel getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(LogLevel logLevel) {
		this.logLevel = logLevel;
	}

	public JobTrigger getMatchedTrigger(ProjectEvent event) {
		for (JobTrigger trigger: getTriggers()) {
			if (trigger.matches(event, this))
				return trigger;
		}
		return null;
	}

	@Override
	public boolean isValid(ConstraintValidatorContext context) {
		Set<String> keys = new HashSet<>();
		Set<String> paths = new HashSet<>();
		
		boolean isValid = true;
		for (JobCache cache: caches) {
			if (keys.contains(cache.getKey())) {
				isValid = false;
				context.buildConstraintViolationWithTemplate("Duplicate key: " + cache.getKey())
						.addPropertyNode("caches").addConstraintViolation();
			} else {
				keys.add(cache.getKey());
			}
			if (paths.contains(cache.getPath())) {
				isValid = false;
				context.buildConstraintViolationWithTemplate("Duplicate path: " + cache.getPath())
						.addPropertyNode("caches").addConstraintViolation();
			} else {
				paths.add(cache.getPath());
			}
		}

		Set<String> dependencyJobs = new HashSet<>();
		for (Dependency dependency: dependencies) {
			if (dependencyJobs.contains(dependency.getJobName())) {
				isValid = false;
				context.buildConstraintViolationWithTemplate("Duplicate dependency: " + dependency.getJobName())
						.addPropertyNode("dependencies").addConstraintViolation();
			} else {
				dependencyJobs.add(dependency.getJobName());
			}
		}
		
		Set<String> promptParamNames = new HashSet<>();
		for (InputSpec promptParam: promptParams) {
			if (promptParamNames.contains(promptParam.getName())) {
				isValid = false;
				context.buildConstraintViolationWithTemplate("Duplicate prompt param: " + promptParam.getName())
						.addPropertyNode("promptParams").addConstraintViolation();
			} else {
				promptParamNames.add(promptParam.getName());
			}
		}
		
		if (!isValid)
			context.disableDefaultConstraintViolation();
		
		return isValid;
	}
	
}
