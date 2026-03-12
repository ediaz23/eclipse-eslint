package com.eclipse.eslint.builder;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

public class ESLintNature implements IProjectNature {

    public static final String NATURE_ID = "com.eclipse.eslint.builder.nature";
    public static final String BUILDER_ID = "com.eclipse.eslint.builder.builder";

    private IProject project;

    @Override
    public void configure() throws CoreException {
        IProjectDescription description = project.getDescription();
        ICommand[] commands = description.getBuildSpec();

        for (ICommand command : commands) {
            if (BUILDER_ID.equals(command.getBuilderName())) {
                return;
            }
        }

        ICommand[] newCommands = new ICommand[commands.length + 1];
        System.arraycopy(commands, 0, newCommands, 0, commands.length);

        ICommand command = description.newCommand();
        command.setBuilderName(BUILDER_ID);
        newCommands[newCommands.length - 1] = command;

        description.setBuildSpec(newCommands);
        project.setDescription(description, null);
    }

    @Override
    public void deconfigure() throws CoreException {
        IProjectDescription description = project.getDescription();
        ICommand[] commands = description.getBuildSpec();

        for (int i = 0; i < commands.length; i++) {
            if (BUILDER_ID.equals(commands[i].getBuilderName())) {
                ICommand[] newCommands = new ICommand[commands.length - 1];
                System.arraycopy(commands, 0, newCommands, 0, i);
                System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);

                description.setBuildSpec(newCommands);
                project.setDescription(description, null);
                return;
            }
        }
    }

    @Override
    public IProject getProject() {
        return project;
    }

    @Override
    public void setProject(IProject project) {
        this.project = project;
    }
}