export class GrailsQuickActions {
  createFloatingActionButton(): string {
    return `
    <div class="grails-fab-container">
      <button class="grails-fab-trigger">
        <svg class="grails-plus-icon" viewBox="0 0 24 24">
          <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" fill="currentColor"/>
        </svg>
      </button>

      <div class="grails-fab-menu">
        <button class="fab-action" data-command="createController">
          <span class="fab-icon">🚀</span>
          <span class="fab-label">Controller</span>
        </button>
        <button class="fab-action" data-command="createService">
          <span class="fab-icon">⚙️</span>
          <span class="fab-label">Service</span>
        </button>
        <button class="fab-action" data-command="createDomain">
          <span class="fab-icon">🗄️</span>
          <span class="fab-label">Domain</span>
        </button>
        <button class="fab-action" data-command="createView">
          <span class="fab-icon">📄</span>
          <span class="fab-label">GSP View</span>
        </button>
      </div>
    </div>`;
  }
}
