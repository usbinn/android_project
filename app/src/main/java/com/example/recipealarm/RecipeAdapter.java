package com.example.recipealarm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecipeListActivity의 RecyclerView에 레시피 목록을 표시하기 위한 어댑터입니다.
 * 레시피 데이터를 UI에 바인딩하고, 사용자 상호작용(클릭, 즐겨찾기)에 대한 이벤트를 처리합니다.
 */
public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    private List<Recipe> recipes = new ArrayList<>();
    private final OnRecipeClickListener recipeClickListener;
    private final OnFavoriteClickListener favoriteClickListener;

    /**
     * 어댑터 생성자
     * @param recipeClickListener 레시피 항목 클릭 리스너
     * @param favoriteClickListener 즐겨찾기 아이콘 클릭 리스너
     */
    public RecipeAdapter(OnRecipeClickListener recipeClickListener, OnFavoriteClickListener favoriteClickListener) {
        this.recipeClickListener = recipeClickListener;
        this.favoriteClickListener = favoriteClickListener;
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        Recipe recipe = recipes.get(position);
        holder.bind(recipe, recipeClickListener, favoriteClickListener);
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    /**
     * RecyclerView에 표시할 레시피 목록을 설정(또는 업데이트)합니다.
     * @param recipes 새로운 레시피 목록
     */
    public void setRecipes(List<Recipe> recipes) {
        this.recipes = recipes;
        notifyDataSetChanged(); // 데이터가 변경되었음을 어댑터에 알립니다.
    }

    /**
     * 각 레시피 항목의 View를 보관하는 ViewHolder 클래스입니다.
     */
    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        private final TextView recipeNameText;
        private final ImageView favoriteIcon;

        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            recipeNameText = itemView.findViewById(R.id.recipe_name_text);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
        }

        public void bind(final Recipe recipe, final OnRecipeClickListener recipeClickListener, final OnFavoriteClickListener favoriteClickListener) {
            recipeNameText.setText(recipe.getName());

            if (recipe.isFavorite()) {
                favoriteIcon.setImageResource(android.R.drawable.btn_star_big_on);
            } else {
                favoriteIcon.setImageResource(android.R.drawable.btn_star_big_off);
            }

            itemView.setOnClickListener(v -> recipeClickListener.onRecipeClick(recipe));
            favoriteIcon.setOnClickListener(v -> favoriteClickListener.onFavoriteClick(recipe));
        }
    }

    /**
     * 레시피 항목 클릭 시 호출될 콜백 인터페이스
     */
    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    /**
     * 즐겨찾기 아이콘 클릭 시 호출될 콜백 인터페이스
     */
    public interface OnFavoriteClickListener {
        void onFavoriteClick(Recipe recipe);
    }
}
