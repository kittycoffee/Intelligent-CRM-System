package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.ProductInfo;
import com.candyd.customeraibiz.mapper.ProductInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/product")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class ProductController {

    @Autowired
    private ProductInfoMapper productMapper;

    /**
     * 1. 列表查询接口
     * 知识点：MyBatis-Plus 的 QueryWrapper,不需要写SQL语句，直接用Java代码.orderByDesc即可组装SQL
     * 用法：orderByDesc("status") 让上架商品排前面，方便管理
     */
    @GetMapping("/list")
    public List<ProductInfo> list() {
        return productMapper.selectList(
                new QueryWrapper<ProductInfo>()
                        .orderByDesc("status") // 状态优先 (1在上, 0在下)
                        .orderByAsc("product_id")   // 同一状态下按 ID 从小到大
                        .orderByDesc("create_time") // 同状态下，新的在上
        );
    }

    /**
     * 2. 新增或更新接口 (二合一)
     * 知识点：根据 ID 是否存在来判断是“新增”还是“修改”
     * 这是一个经典的后端设计模式，前端只需调这一个接口即可
     */
    @PostMapping("/save")
    public String save(@RequestBody ProductInfo product) {
        if (product.getProductId() == null) {
            // ID 为空 -> 新增
            product.setCreateTime(LocalDateTime.now());
            product.setStatus(1); // 默认上架
            productMapper.insert(product);
        } else {
            // ID 不为空 -> 更新
            productMapper.updateById(product);
        }
        return "success";
    }

    /**
     * 3. 上架/下架 切换接口
     * 知识点：局部更新
     * 我们不需要传整个商品对象，只需要传 ID 和 目标状态 即可，节省带宽
     */
    @PostMapping("/status")
    public String toggleStatus(@RequestBody ProductInfo product) {
        // 这里 product 对象里只有 id 和 status 两个字段有值
        productMapper.updateById(product);
        return "success";
    }

    /**
     * 4. 删除接口
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Integer id) {
        productMapper.deleteById(id);
        return "success";
    }
}