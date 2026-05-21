use spacetimedb::{table, reducer, ReducerContext, Table};

#[table(accessor = person, public)]
pub struct Person {
    #[primary_key]
    pub name: String,
    pub age: u32,
}

#[reducer]
pub fn add_person(ctx: &ReducerContext, name: String, age: u32) {
    ctx.db.person().insert(Person { name, age });
}
