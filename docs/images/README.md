# Diagram visual system

These SVGs are the editable source assets. Edit their text and coordinates directly; no diagram generator, external font, logo or linked asset is required. Each file includes an accessible title and description and an explicit light background for GitHub's light and dark themes.

| Element | Convention |
| --- | --- |
| Canvas | 1000-unit width; off-white `#fafaf7`; 36-unit outer margin |
| Typography | Arial, Helvetica, sans-serif; 32-unit title, 24-unit component heading, 20-unit supporting text; 22-unit emphasis |
| Storefront | Blue: `#edf3fc` fill, `#4775ac` border |
| Order Processing | Teal: `#eaf5f2` fill, `#357c70` border |
| Supplier / partial shipment | Ochre: `#fcf3e4` fill, `#a37834` border |
| MongoDB | Green cylinders: `#eef4ea` fill, `#65815a` border |
| Artemis / events | Violet lane and pills: `#f2eef9` fill, `#8264a3` border |
| Browser / mail / legacy components | Neutral: `#f1f3f5` fill, `#85919c` border |
| Denial / warning | Muted red: `#fbefeb` fill, `#aa6353` border |
| Completion | Green: `#dceee5` fill, `#357c70` double border in the state diagram |
| Text / connectors | Charcoal `#25313c` text; `#596572` connectors; 2-unit lines and matching arrowheads |
| Shapes | 12-unit rounded cards; database cylinders; compact event pills; 20-unit inner padding |

Solid arrows show synchronous HTTP in the architecture and checkout views; other solid connectors represent the labeled processing, persistence or SMTP steps. Dashed arrows identify asynchronous messaging or event-driven state transitions. Plain lines in the legacy entity view represent relationships. Local legends explain the context.

The architecture repeats producer and consumer endpoints within the Artemis lane to make each queue direction explicit without crossing connectors. Repeated endpoints refer to the same services, not additional deployments. Notification Listener and Spring Mail run within Order Processing. Mailpit appears only as local development infrastructure.

Keep labels short and leave detailed contracts and failure handling in the surrounding documentation. Preserve actual names, ports, boundaries and event direction. Preview edits at both 1000px and 700px wide, validate XML, and check the Markdown image references. SVGs can be reused at any resolution or rasterized for slide software that requires PNG.
