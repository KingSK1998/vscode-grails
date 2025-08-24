/** LSP Work Done Progress types from the spec */
export interface WorkDoneProgressBegin {
  kind: "begin";
  title: string;
  message?: string;
  percentage?: number;
  cancellable?: boolean;
}

export interface WorkDoneProgressReport {
  kind: "report";
  message?: string;
  percentage?: number;
  cancellable?: boolean;
}

export interface WorkDoneProgressEnd {
  kind: "end";
  message?: string;
}

export type WorkDoneProgress = WorkDoneProgressBegin | WorkDoneProgressReport | WorkDoneProgressEnd;

/** LSP Message types */
export enum MessageType {
  Error = 1,
  Warning = 2,
  Info = 3,
  Log = 4,
}
