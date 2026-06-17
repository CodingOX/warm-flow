```json
[
  {
    "type": "input",
    "field": "goods_name",
    "title": "商品名称",
    "value": "智能手机",
    "props": {
      "placeholder": "请输入商品名称",
      "clearable": true
    },
    "validate": [
      { "required": true, "message": "商品名称不能为空", "trigger": "blur" },
      { "min": 3, "max": 15, "message": "长度在 3 到 15 个字符", "trigger": "blur" }
    ]
  },
  {
    "type": "select",
    "field": "category_id",
    "title": "所属分类",
    "value": 2,
    "props": {
      "placeholder": "请选择分类"
    },
    "options": [
      { "value": 1, "label": "服装鞋帽" },
      { "value": 2, "label": "数码电子" }
    ],
    "validate": [
      { "required": true, "message": "请选择分类", "trigger": "change" }
    ]
  },
  {
    "type": "row",
    "title": "栅格布局容器",
    "children": [
      {
        "type": "col",
        "props": { "span": 12 },
        "children": [
          {
            "type": "inputNumber",
            "field": "price",
            "title": "商品价格",
            "value": 0
          }
        ]
      },
      {
        "type": "col",
        "props": { "span": 12 },
        "children": [
          {
            "type": "inputNumber",
            "field": "stock",
            "title": "商品库存",
            "value": 99
          }
        ]
      }
    ]
  },
  {
    "type": "subForm",
    "field": "specifications",
    "title": "商品规格(一对多子表单)",
    "value": [],
    "props": {
      "rule": [
        {
          "type": "input",
          "field": "spec_name",
          "title": "规格名",
          "props": { "placeholder": "如：颜色、内存" }
        },
        {
          "type": "input",
          "field": "spec_value",
          "title": "规格值",
          "props": { "placeholder": "如：红色、64G" }
        }
      ]
    }
  }
]

```