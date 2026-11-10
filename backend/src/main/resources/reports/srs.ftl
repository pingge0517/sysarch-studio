<#ftl output_format="XHTML" encoding="UTF-8">
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>${system.name} 系统规格书</title>
<#include "_style.ftl">
</head>
<body>

<div class="cover">
  <h1>${system.name}<br />系统规格书</h1>
  <p>项目：${project.name}</p>
  <p>依据：${project.baselineLabel}</p>
  <p>负责人：${system.owner}</p>
  <p>生成时间：${project.generatedAt}</p>
  <p class="muted">由 SysArch Studio 从模型自动生成</p>
</div>

<div class="chapter">
  <h2>目录</h2>
  <ul>
    <li>1 系统概述</li>
    <li>2 分层架构
      <ul><#list layers as l><li>2.${l?counter} ${l.name}</li></#list></ul>
    </li>
    <li>3 接口清单</li>
    <li>4 需求追溯表</li>
    <li>5 待补充</li>
    <li>附录 A 元素明细</li>
  </ul>
</div>

<div class="chapter">
  <h2>1 系统概述</h2>
  <h3>1.1 系统</h3>
  <table class="meta">
    <tr><td>系统名称</td><td>${system.name}</td></tr>
    <tr><td>说明</td><td>${system.description}</td></tr>
    <tr><td>负责人</td><td>${system.owner}</td></tr>
    <tr><td>依据</td><td>${project.baselineLabel}（项目 ${project.name}）</td></tr>
  </table>
  <h3>1.2 模型规模</h3>
  <table>
    <tr><th>元素总数</th><th>连接</th><th>接口（点对点）</th><th>需求</th><th>需求覆盖率</th></tr>
    <tr><td>${stats.elementCount}</td><td>${stats.connectionCount}</td><td>${stats.interfaceCount}</td><td>${stats.requirementCount}</td><td>${stats.coverage}</td></tr>
  </table>
  <h3>1.3 分层概览</h3>
  <table>
    <tr><th>层</th><th>结构元素数</th><th>元素</th></tr>
    <#list layers as l>
    <tr><td>${l.name}</td><td>${l.elements?size}</td><td><#list l.elements as e>${e.name}<#sep>、</#sep></#list></td></tr>
    </#list>
  </table>
</div>

<#list layers as l>
<div class="chapter">
  <h2>2.${l?counter} ${l.name}</h2>
  <p>本层共有 ${l.elements?size} 个结构元素（块与部件）。</p>
  <table>
    <tr><th>编号</th><th>名称</th><th>类别</th><th>类型</th><th>构造型</th><th>说明</th></tr>
    <#list l.elements as e>
    <tr><td>${e.id}</td><td>${e.name}</td><td>${e.type}</td><td>${e.typedBy}</td><td><#list e.stereotypes as s>${s}<#sep>、</#sep></#list></td><td>${e.description}</td></tr>
    </#list>
  </table>
  <#list l.elements as e>
  <h3>2.${l?counter}.${e?counter} ${e.name}（${e.id}）</h3>
  <p>${e.type}<#list e.stereotypes as s>，构造型 ${s}</#list>。${e.description}</p>
  <h4>端口</h4>
  <table>
    <tr><th>编号</th><th>名称</th><th>方向</th><th>接口</th></tr>
    <#list e.ports as p>
    <tr><td>${p.id}</td><td>${p.name}</td><td>${p.direction}</td><td>${p.interface}</td></tr>
    <#else>
    <tr><td colspan="4" class="muted">无端口</td></tr>
    </#list>
  </table>
  <h4>连接</h4>
  <table>
    <tr><th>编号</th><th>名称</th><th>源</th><th>目标</th><th>接口</th></tr>
    <#list e.connections as c>
    <tr><td>${c.id}</td><td>${c.name}</td><td>${c.source}</td><td>${c.target}</td><td>${c.interface}</td></tr>
    <#else>
    <tr><td colspan="5" class="muted">无连接</td></tr>
    </#list>
  </table>
  </#list>
</div>
</#list>

<div class="chapter">
  <h2>3 接口清单</h2>
  <h3>3.1 点对点接口（共 ${stats.interfaceCount} 个）</h3>
  <table>
    <tr><th>编号</th><th>名称</th><th>接口</th><th>协议</th><th>数据类型</th><th>源端</th><th>目标端</th><th>层</th><th>关联需求</th></tr>
    <#list interfaces as i>
    <tr><td>${i.id}</td><td>${i.name}</td><td>${i.interface}</td><td>${i.protocol}</td><td>${i.dataType}</td><td>${i.sourceElement}</td><td>${i.targetElement}</td><td>${i.layer}</td><td><#list i.requirements as r>${r}<#sep>、</#sep></#list></td></tr>
    <#else>
    <tr><td colspan="9" class="muted">无</td></tr>
    </#list>
  </table>
  <h3>3.2 接口定义</h3>
  <table>
    <tr><th>编号</th><th>名称</th><th>协议</th><th>数据类型</th><th>层</th><th>使用次数</th></tr>
    <#list interfaceDefs as d>
    <tr><td>${d.id}</td><td>${d.name}</td><td>${d.protocol}</td><td>${d.dataType}</td><td>${d.layer}</td><td>${d.usages}</td></tr>
    <#else>
    <tr><td colspan="6" class="muted">无</td></tr>
    </#list>
  </table>
</div>

<div class="chapter">
  <h2>4 需求追溯表</h2>
  <p>需求 ${stats.requirementCount} 条，已满足 ${stats.satisfied} 条，已验证 ${stats.verified} 条，未满足 ${stats.unsatisfied} 条，覆盖率 ${stats.coverage}。</p>
  <table>
    <tr><th>需求编号</th><th>名称</th><th>层</th><th>满足者</th><th>验证者</th><th>状态</th></tr>
    <#list requirements as r>
    <tr><td>${r.reqId}</td><td>${r.name}</td><td>${r.layer}</td><td><#list r.satisfiedBy as s>${s}<#sep>、</#sep></#list></td><td><#list r.verifiedBy as v>${v}<#sep>、</#sep></#list></td><td class="${r.statusClass}">${r.status}</td></tr>
    </#list>
  </table>
  <h3>需求正文</h3>
  <#list requirements as r>
  <h4>${r.reqId} ${r.name}</h4>
  <p>${r.text}</p>
  <p class="muted">验证方法 ${r.verifyMethod}；优先级 ${r.priority}；状态 <span class="${r.statusClass}">${r.status}</span></p>
  </#list>
</div>

<div class="chapter">
  <h2>5 待补充</h2>
  <p>以下是模型中尚不完整的地方，报告照常生成，仅在此列出。</p>
  <#list gaps as g>
  <h3>${g.title}</h3>
  <ul><#list g.items as item><li>${item}</li></#list></ul>
  <#else>
  <p>无。</p>
  </#list>
</div>

<div class="chapter">
  <h2>附录 A 元素明细</h2>
  <#list elements as e>
  <h3>A.${e?counter} ${e.name}（${e.id}）</h3>
  <table>
    <tr><th style="width: 30%">字段</th><th>值</th></tr>
    <tr><td>类别</td><td>${e.type}</td></tr>
    <tr><td>层</td><td>${e.layerName}</td></tr>
    <#list e.details as d>
    <tr><td>${d.key}</td><td>${d.value}</td></tr>
    </#list>
  </table>
  </#list>
</div>

</body>
</html>
