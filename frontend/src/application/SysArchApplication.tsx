import { ExtensionRegistry, type ExtensionRegistryMergeStrategy } from '@eclipse-sirius/sirius-components-core';
import {
  DiagramRepresentationConfiguration,
  SiriusWebApplication,
  type NodeTypeRegistry,
  navigationBarRightContributionExtensionPoint,
  routerExtensionPoint,
} from '@eclipse-sirius/sirius-web-application';
import '@xyflow/react/dist/style.css';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
// not in the package's exports, so by path
import '../../node_modules/@eclipse-sirius/sirius-components-portals/dist/sirius-components-portals.css';
import { App } from '../App';
import { EllipseNodeConverter } from './EllipseNodeConverter';
import { mergeComponents, mergeData } from './merge';
import { SignInGate } from './SignInGate';

/**
 * One interface (P4 T3, spec P4 §3): Sirius Web's own application (project browser, diagram editor, explorer,
 * properties, validation) is the host, from Sirius' published front-end packages at the backend's version; SysArch's
 * shell (matrices, baselines, branches, reports, imports, comments, administration) is contributed through Sirius'
 * extension points: the route /sysarch and a link in the navigation bar. Sirius itself is used as published (no source
 * change, no renderer replacement: P0 §2.3).
 */
const registry = new ExtensionRegistry();
registry.putData(routerExtensionPoint, {
  identifier: 'sysarch_routes',
  data: [{ path: '/sysarch', element: <App /> }],
});
registry.addComponent(navigationBarRightContributionExtensionPoint, {
  identifier: 'sysarch_navigation',
  Component: () => (
    <a href="/sysarch" className="sysarch-nav-link">
      SysArch 表格与报告
    </a>
  ),
});

const mergeStrategy: ExtensionRegistryMergeStrategy = {
  mergeComponentExtensions: (_identifier, existing, added) => mergeComponents(existing, added),
  mergeDataExtensions: (_identifier, existing, added) => mergeData(existing, added),
};

// Sirius Web's ellipse node (initial states, use cases) is drawn by our converter: its React side is in Sirius Web's
// own application only, not in the published packages (EllipseNodeConverter).
const nodeTypeRegistry: NodeTypeRegistry = { nodeConverters: [new EllipseNodeConverter()], nodeLayoutHandlers: [], nodeTypeContributions: [] };

export function SysArchApplication() {
  const httpOrigin = window.location.origin;
  const wsOrigin = httpOrigin.replace(/^http/, 'ws');
  return (
    <SignInGate>
      <SiriusWebApplication httpOrigin={httpOrigin} wsOrigin={wsOrigin} extensionRegistry={registry} extensionRegistryMergeStrategy={mergeStrategy}>
        <DiagramRepresentationConfiguration nodeTypeRegistry={nodeTypeRegistry} />
      </SiriusWebApplication>
    </SignInGate>
  );
}
