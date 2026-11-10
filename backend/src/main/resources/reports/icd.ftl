<#ftl output_format="XHTML" encoding="UTF-8">
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>${project.name} 接口数据清单</title>
<#include "_style.ftl">
</head>
<body>

<div class="cover">
  <h1>${project.name}<br />接口数据清单（ICD）</h1>
  <p>依据：${project.baselineLabel}</p>
  <p>生成时间：${project.generatedAt}</p>
  <p class="muted">由 SysArch Studio 从模型自动生成</p>
</div>

<div class="chapter">
  <h2>1 概述</h2>
  <p>本清单逐条列出模型中的点对点接口（连接）共 ${stats.interfaceCount} 个，以及它们引用的接口定义共 ${interfaceDefs?size} 个。</p>
  <h2>2 接口逐条</h2>
  <#list interfaces as i>
  <h3>2.${i?counter} ${i.name}（${i.id}）</h3>
  <table>
    <tr><th style="width: 25%">项</th><th>内容</th></tr>
    <tr><td>名称</td><td>${i.name}</td></tr>
    <tr><td>接口定义</td><td>${i.interface}</td></tr>
    <tr><td>协议</td><td>${i.protocol}</td></tr>
    <tr><td>数据类型</td><td>${i.dataType}</td></tr>
    <tr><td>源端</td><td>${i.sourceElement}</td></tr>
    <tr><td>目标端</td><td>${i.targetElement}</td></tr>
    <tr><td>所属层</td><td>${i.layer}</td></tr>
    <tr><td>关联需求</td><td><#list i.requirements as r>${r}<#sep>、</#sep><#else>无</#list></td></tr>
  </table>
  <#else>
  <p>模型里没有连接。</p>
  </#list>
</div>

<div class="chapter">
  <h2>3 接口汇总表</h2>
  <table>
    <tr><th>编号</th><th>名称</th><th>协议</th><th>数据类型</th><th>源端</th><th>目标端</th><th>层</th><th>关联需求</th></tr>
    <#list interfaces as i>
    <tr><td>${i.id}</td><td>${i.name}</td><td>${i.protocol}</td><td>${i.dataType}</td><td>${i.sourceElement}</td><td>${i.targetElement}</td><td>${i.layer}</td><td><#list i.requirements as r>${r}<#sep>、</#sep></#list></td></tr>
    </#list>
  </table>
  <h2>4 接口定义</h2>
  <table>
    <tr><th>编号</th><th>名称</th><th>协议</th><th>数据类型</th><th>层</th><th>使用次数</th></tr>
    <#list interfaceDefs as d>
    <tr><td>${d.id}</td><td>${d.name}</td><td>${d.protocol}</td><td>${d.dataType}</td><td>${d.layer}</td><td>${d.usages}</td></tr>
    <#else>
    <tr><td colspan="6" class="muted">无</td></tr>
    </#list>
  </table>
  <h2>5 待补充</h2>
  <#list gaps as g>
  <h3>${g.title}</h3>
  <ul><#list g.items as item><li>${item}</li></#list></ul>
  <#else>
  <p>无。</p>
  </#list>
</div>

</body>
</html>
