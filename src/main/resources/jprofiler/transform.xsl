<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:param name="id"/>
  <xsl:param name="allocRecording"/>
  <xsl:param name="monitorRecording"/>
  <xsl:param name="probesFile"/>
  <xsl:param name="snapshotPath"/>
  <xsl:param name="captureOnJvmStop"/>

  <xsl:template match="/|@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="/config/sessions/session[@id=$id]/triggers">
    <xsl:copy>
      <jvmStart enabled="true">
        <actions>
          <startRecording>
            <cpu enabled="true" resetData="true"/>
            <allocation enabled="{$allocRecording}" resetData="true"/>
            <thread enabled="false"/>
            <telemetry enabled="false"/>
            <methodStats enabled="false"/>
            <complexity enabled="false"/>
          </startRecording>
          <xsl:if test="$monitorRecording">
            <startMonitorRecording blockingThreshold="1000" waitingThreshold="100000"/>
          </xsl:if>
          <xsl:for-each select="document($probesFile)//probe">
            <startProbeRecording name="{@name}" events="{@events}" recordSpecial="{@recordSpecial}"/>
          </xsl:for-each>
        </actions>
      </jvmStart>
      <xsl:if test="$captureOnJvmStop">
      <jvmStop enabled="true">
        <actions>
          <saveSnapshot file="{$snapshotPath}" number="false"/>
        </actions>
      </jvmStop>
      </xsl:if>
      <xsl:apply-templates/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
