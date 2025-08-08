package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {


    /**
     * 根据用户id查询购物车列表
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 根据购物车id更新购物车数量
     * @param shoppingCart
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById (ShoppingCart shoppingCart);

    /**
     * 插入购物车数据
     * @param shoppingCart
     */
    @Insert("insert into shopping_cart (name,image,amount,number,create_time,user_id,dish_id,setmeal_id) " +
            "values (#{name},#{image},#{amount},#{number},#{createTime},#{userId},#{dishId},#{setmealId})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 根据用户id删除购物车数据
     * @param id
     */
    @Delete("delete from shopping_cart where user_id = #{id}")
    void deleteByUserId(Long id);

    /**
     * 删除购物车数据
     * @param shoppingCart
     */
    void delete(ShoppingCart shoppingCart);
}
