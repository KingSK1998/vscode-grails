export class GrailsProjectCard {
  createProjectCard(): string {
    return `
    <div class="grails-project-card">
      <div class="project-header">
        <div class="grails-logo-container">
          <svg class="grails-logo" viewBox="0 0 100 100">
            <!-- Custom Grails leaf logo SVG -->
            <path d="M20 80 Q50 20 80 80 Q50 60 20 80" fill="var(--grails-primary)"/>
          </svg>
        </div>
        <div class="project-info">
          <h2 class="project-name">{{projectName}}</h2>
          <span class="grails-version-badge">Grails {{version}}</span>
        </div>
        <div class="server-status">
          <div class="status-indicator {{statusClass}}"></div>
          <span>{{statusText}}</span>
        </div>
      </div>

      <div class="quick-stats">
        <div class="stat-item">
          <span class="stat-icon">🚀</span>
          <span class="stat-number">{{controllerCount}}</span>
          <span class="stat-label">Controllers</span>
        </div>
        <div class="stat-item">
          <span class="stat-icon">⚙️</span>
          <span class="stat-number">{{serviceCount}}</span>
          <span class="stat-label">Services</span>
        </div>
        <div class="stat-item">
          <span class="stat-icon">🗄️</span>
          <span class="stat-number">{{domainCount}}</span>
          <span class="stat-label">Domains</span>
        </div>
      </div>
    </div>`;
  }
}
