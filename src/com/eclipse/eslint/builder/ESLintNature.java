package com.eclipse.eslint.builder;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.FrameworkUtil;

public class ESLintNature implements IProjectNature {

    public static final String NATURE_ID = "com.eclipse.eslint.builder.nature";
    public static final String BUILDER_ID = "com.eclipse.eslint.builder.builder";

    private static final String PLUGIN_ID = "com.eclipse.eslint.builder";
    private static final ILog LOG = Platform.getLog(FrameworkUtil.getBundle(ESLintNature.class));

    private IProject project;

    @Override
    public void configure() throws CoreException {
        System.out.println("[ESLintNature] configure() project=" + project.getName());
        LOG.log(new Status(IStatus.INFO, PLUGIN_ID, "configure() project=" + project.getName()));

        try {
            IProjectDescription description = project.getDescription();
            ICommand[] commands = description.getBuildSpec();

            for (ICommand command : commands) {
                if (BUILDER_ID.equals(command.getBuilderName())) {
                    System.out.println("[ESLintNature] builder already present project=" + project.getName());
                    LOG.log(new Status(IStatus.INFO, PLUGIN_ID,
                            "builder already present project=" + project.getName()));
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

            System.out.println("[ESLintNature] builder added project=" + project.getName());
            LOG.log(new Status(IStatus.INFO, PLUGIN_ID, "builder added project=" + project.getName()));
        } catch (CoreException e) {
            LOG.log(new Status(IStatus.ERROR, PLUGIN_ID,
                    "configure() failed project=" + project.getName(), e));
            throw e;
        }
    }

    @Override
    public void deconfigure() throws CoreException {
        System.out.println("[ESLintNature] deconfigure() project=" + project.getName());
        LOG.log(new Status(IStatus.INFO, PLUGIN_ID, "deconfigure() project=" + project.getName()));

        try {
            IProjectDescription description = project.getDescription();
            ICommand[] commands = description.getBuildSpec();

            for (int i = 0; i < commands.length; i++) {
                if (BUILDER_ID.equals(commands[i].getBuilderName())) {
                    ICommand[] newCommands = new ICommand[commands.length - 1];
                    System.arraycopy(commands, 0, newCommands, 0, i);
                    System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);

                    description.setBuildSpec(newCommands);
                    project.setDescription(description, null);

                    System.out.println("[ESLintNature] builder removed project=" + project.getName());
                    LOG.log(new Status(IStatus.INFO, PLUGIN_ID, "builder removed project=" + project.getName()));
                    return;
                }
            }

            System.out.println("[ESLintNature] builder not found project=" + project.getName());
            LOG.log(new Status(IStatus.INFO, PLUGIN_ID, "builder not found project=" + project.getName()));
        } catch (CoreException e) {
            LOG.log(new Status(IStatus.ERROR, PLUGIN_ID,
                    "deconfigure() failed project=" + project.getName(), e));
            throw e;
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