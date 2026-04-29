import React, { useState, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { adminDb } from '@/lib/adminDb';
import { Category } from '@/types/admin';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogClose } from '@/components/ui/dialog';
import { Plus, Edit2, Trash2, GripVertical, Folder, Eye, EyeOff } from 'lucide-react';
import { toast } from 'sonner';

interface CategoryManagerProps {
  onSelectCategory: (category: Category | null) => void;
  selectedCategoryId: string | null;
}

const CategoryManager: React.FC<CategoryManagerProps> = ({ onSelectCategory, selectedCategoryId }) => {
  const [categories, setCategories] = useState<Record<string, Category>>({});
  const [newCategoryName, setNewCategoryName] = useState('');
  const [editCategory, setEditCategory] = useState<Category | null>(null);
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);

  const loadCategories = async () => {
    const { data, error } = await supabase
      .from('categories')
      .select('*, channels(*)')
      .order('sort_order');
    if (error) {
      toast.error('فشل تحميل الأقسام: ' + error.message);
      return;
    }
    const map: Record<string, Category> = {};
    for (const c of data ?? []) {
      const channelsObj: Record<string, any> = {};
      for (const ch of (c as any).channels ?? []) channelsObj[ch.id] = ch;
      map[c.id] = {
        id: c.id,
        name: c.name,
        sortOrder: c.sort_order ?? 0,
        hidden: c.hidden,
        channels: channelsObj,
      } as Category;
    }
    setCategories(map);
  };

  useEffect(() => {
    loadCategories();
    const channel = supabase
      .channel('categories-changes')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'categories' }, () => loadCategories())
      .on('postgres_changes', { event: '*', schema: 'public', table: 'channels' }, () => loadCategories())
      .subscribe();
    return () => { supabase.removeChannel(channel); };
  }, []);

  const handleAddCategory = async () => {
    if (!newCategoryName.trim()) return;
    try {
      const sortOrder = Object.keys(categories).length;
      await adminDb.insert('categories', {
        name: newCategoryName.trim(),
        sort_order: sortOrder,
        legacy_id: null,
      });
      toast.success('تم إضافة القسم وتشفير البيانات');
      setNewCategoryName('');
      setIsAddDialogOpen(false);
    } catch (err: any) {
      toast.error('فشل الإضافة: ' + err.message);
    }
  };

  const handleUpdateCategory = async () => {
    if (!editCategory || !editCategory.name.trim()) return;
    try {
      await adminDb.update('categories', { id: editCategory.id }, { name: editCategory.name.trim() });
      toast.success('تم التعديل وإعادة التشفير');
      setEditCategory(null);
      setIsEditDialogOpen(false);
    } catch (err: any) {
      toast.error('فشل التعديل: ' + err.message);
    }
  };

  const handleDeleteCategory = async (categoryId: string) => {
    if (!confirm('هل أنت متأكد أنك تريد حذف هذا القسم وجميع القنوات بداخله؟')) return;
    try {
      await adminDb.delete('channels', { category_id: categoryId }, true);
      await adminDb.delete('categories', { id: categoryId });
      toast.success('تم الحذف');
      if (selectedCategoryId === categoryId) onSelectCategory(null);
    } catch (err: any) {
      toast.error('فشل الحذف: ' + err.message);
    }
  };

  const handleMoveCategory = async (categoryId: string, direction: 'up' | 'down') => {
    const sorted = Object.values(categories).sort((a, b) => a.sortOrder - b.sortOrder);
    const i = sorted.findIndex(c => c.id === categoryId);
    try {
      if (direction === 'up' && i > 0) {
        const prev = sorted[i - 1], curr = sorted[i];
        await adminDb.update('categories', { id: categoryId }, { sort_order: prev.sortOrder }, true);
        await adminDb.update('categories', { id: prev.id }, { sort_order: curr.sortOrder });
      } else if (direction === 'down' && i < sorted.length - 1) {
        const next = sorted[i + 1], curr = sorted[i];
        await adminDb.update('categories', { id: categoryId }, { sort_order: next.sortOrder }, true);
        await adminDb.update('categories', { id: next.id }, { sort_order: curr.sortOrder });
      }
    } catch (err: any) {
      toast.error('فشل الترتيب: ' + err.message);
    }
  };

  const sortedCategories = Object.values(categories).sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <Card className="border-border bg-card">
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-lg font-bold text-foreground">الأقسام</CardTitle>
        <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
          <DialogTrigger asChild>
            <Button size="sm" className="bg-primary text-primary-foreground">
              <Plus className="w-4 h-4 mr-2" />
              إضافة
            </Button>
          </DialogTrigger>
          <DialogContent className="bg-card border-border">
            <DialogHeader><DialogTitle>إضافة قسم جديد</DialogTitle></DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label>اسم القسم</Label>
                <Input value={newCategoryName} onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="مثال: رياضة، أفلام، أخبار" className="bg-secondary border-border" />
              </div>
            </div>
            <DialogFooter>
              <DialogClose asChild><Button variant="outline">إلغاء</Button></DialogClose>
              <Button onClick={handleAddCategory} className="bg-primary text-primary-foreground">إضافة القسم</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </CardHeader>
      <CardContent className="space-y-2">
        {sortedCategories.length === 0 ? (
          <p className="text-muted-foreground text-sm text-center py-4">لا توجد أقسام بعد</p>
        ) : (
          sortedCategories.map((category, index) => (
            <div key={category.id}
              className={`flex items-center gap-2 p-3 rounded-lg cursor-pointer transition-colors ${
                selectedCategoryId === category.id
                  ? 'bg-primary/20 border border-primary'
                  : 'bg-secondary hover:bg-secondary/80'
              }`}
              onClick={() => onSelectCategory(category)}>
              <div className="flex flex-col gap-0.5">
                <Button variant="ghost" size="icon" className="h-4 w-4 p-0"
                  onClick={(e) => { e.stopPropagation(); handleMoveCategory(category.id, 'up'); }}
                  disabled={index === 0}>
                  <GripVertical className="w-3 h-3 rotate-90" />
                </Button>
                <Button variant="ghost" size="icon" className="h-4 w-4 p-0"
                  onClick={(e) => { e.stopPropagation(); handleMoveCategory(category.id, 'down'); }}
                  disabled={index === sortedCategories.length - 1}>
                  <GripVertical className="w-3 h-3 rotate-90" />
                </Button>
              </div>
              <Folder className="w-5 h-5 text-primary" />
              <span className={`flex-1 font-medium ${category.hidden ? 'text-muted-foreground line-through' : 'text-foreground'}`}>
                {category.name}
                {category.hidden && <span className="text-xs text-amber-500 mr-2">(مخفي)</span>}
              </span>
              <span className="text-xs text-muted-foreground">
                {Object.keys(category.channels || {}).length} قناة
              </span>
              <Button variant="ghost" size="icon" className="h-8 w-8"
                title={category.hidden ? 'إظهار القسم' : 'إخفاء القسم'}
                onClick={async (e) => {
                  e.stopPropagation();
                  try { await adminDb.update('categories', { id: category.id }, { hidden: !category.hidden }); }
                  catch (err: any) { toast.error(err.message); }
                }}>
                {category.hidden ? <EyeOff className="w-4 h-4 text-amber-500" /> : <Eye className="w-4 h-4" />}
              </Button>
              <Button variant="ghost" size="icon" className="h-8 w-8"
                onClick={(e) => { e.stopPropagation(); setEditCategory(category); setIsEditDialogOpen(true); }}>
                <Edit2 className="w-4 h-4" />
              </Button>
              <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive"
                onClick={(e) => { e.stopPropagation(); handleDeleteCategory(category.id); }}>
                <Trash2 className="w-4 h-4" />
              </Button>
            </div>
          ))
        )}
      </CardContent>

      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent className="bg-card border-border">
          <DialogHeader><DialogTitle>تعديل القسم</DialogTitle></DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>اسم القسم</Label>
              <Input value={editCategory?.name || ''}
                onChange={(e) => setEditCategory(prev => prev ? { ...prev, name: e.target.value } : null)}
                className="bg-secondary border-border" />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild><Button variant="outline">إلغاء</Button></DialogClose>
            <Button onClick={handleUpdateCategory} className="bg-primary text-primary-foreground">حفظ التغييرات</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
};

export default CategoryManager;
