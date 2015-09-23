/*
 * Copyright (c) 2008-2013 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/license for details.
 */

import org.apache.commons.lang.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

import java.text.SimpleDateFormat

/**
 * Generates <b>release.timestamp</b> and <b>release.number</b> files by artifact version and optional VCS version
 *
 * @author artamonov
 * @version $Id$
 */
class CubaReleaseTimeStamp extends DefaultTask {

    String releaseTimeStampPath
    String releaseNumberPath

    String artifactVersion = project.cuba.artifact.version
    Boolean isSnapshot = project.cuba.artifact.isSnapshot
    String buildVcsNumber = project.rootProject.hasProperty('buildVcsNumber') ?
        project.rootProject['buildVcsNumber'] : null

    CubaReleaseTimeStamp() {
        setDescription('Generates release timestamp and release number files with optional VCS version')
        setGroup('Util')
        // set default task dependsOn
        setDependsOn(project.getTasksByName('compileJava', false))
        project.getTasksByName('classes', false).each { it.dependsOn(this) }
    }

    @InputDirectory
    def getInputDirectory() {
        return new File("$project.projectDir/src")
    }

    @OutputFiles
    def List getOutputFiles() {
        return [new File(releaseTimeStampPath), new File(releaseNumberPath)]
    }

    @TaskAction
    def generateReleaseFiles() {
        if (!releaseNumberPath)
            throw new IllegalStateException('Not specified releaseNumberPath param for CubaReleaseTimeStamp');

        if (!releaseTimeStampPath)
            throw new IllegalStateException('Not specified releaseTimeStampPath param for CubaReleaseTimeStamp');

        if (!artifactVersion)
            throw new IllegalStateException('Not specified artifactVersion param for CubaReleaseTimeStamp');

        if (isSnapshot == null)
            throw new IllegalStateException('Not specified isSnapshot flag for CubaReleaseTimeStamp');

        Date releaseDate = new Date()
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(releaseDate)

        String releaseNumber = artifactVersion
        if (isSnapshot) {
            if (StringUtils.isNotBlank(buildVcsNumber))
                releaseNumber = releaseNumber + '.' + buildVcsNumber
            releaseNumber = releaseNumber + '-SNAPSHOT'
        }

        File releaseFile = new File(releaseTimeStampPath)
        File releaseNumberFile = new File(releaseNumberPath)

        releaseFile.delete()
        releaseFile.write(timeStamp)

        project.logger.lifecycle("Release timestamp: $timeStamp")

        releaseNumberFile.delete()
        releaseNumberFile.write(releaseNumber)

        project.logger.lifecycle("Release number: $releaseNumber")
    }
}