// Shared TypeScript type definitions for Grails LSP

export interface GrailsLspConfig {
	completionDetail: "BASIC" | "STANDARD" | "ADVANCED";
	maxCompletionItems: number;
	includeSnippets: boolean;
	enableGrailsMagic: boolean;
	codeLensMode: "OFF" | "BASIC" | "ADVANCED" | "FULL";
	compilerPhase: number;
	shouldRecompileOnChange: boolean;
}

export interface GrailsServerConfig {
	javaHome?: string;
	port: number;
	host: string;
	jvmArgs: string[];
	developmentMode: boolean;
}

export interface GrailsArtefact {
	name: string;
	type: "controller" | "service" | "domain" | "taglib" | "job" | "command";
	path: string;
	file: string;
	packageName?: string;
}

export interface ServerConnectionState {
	connected: boolean;
	mode: "development" | "production";
	retryCount: number;
	lastError?: string;
}

export interface LSPCommand {
	command: string;
	title: string;
	category: string;
	arguments?: any[];
}
